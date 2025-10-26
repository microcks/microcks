/*
 * Copyright The Microcks Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import {
  Component,
  signal,
  viewChild,
  AfterViewInit,
  Input,
  OnInit,
  OnDestroy,
  inject,
} from "@angular/core";
import { CommonModule } from "@angular/common";
import { ActivatedRoute } from "@angular/router";
import * as d3h from "d3-hierarchy";
import {
  DynamicNode,
  Edge,
  VflowComponent,
  Vflow,
  ComponentDynamicNode,
} from "ngx-vflow";
import { Observable, Subject, Subscription, map } from "rxjs";
import { LiveTracesControlsComponent } from "../live-traces-controls/live-traces-controls.component";
import { LiveTracesManagerService } from "../live-traces-manager.service";
import { ReadableSpan } from "@opentelemetry/sdk-trace-base";
import { ExternalUserNodeComponent } from "./nodes/external-user-node.component";
import { ServiceNodeComponent } from "./nodes/service-node.component";
import { OperationNodeComponent } from "./nodes/operation-node.component";

/**
 * Hierarchy node data structure for d3-hierarchy.
 */
interface HierarchyNodeData {
  id: string;
  kind: "app" | "service" | "operation";
  label: string;
  type?: typeof ExternalUserNodeComponent;
  serviceName?: string;
  clientIpFilter?: string;
  children?: HierarchyNodeData[];
}

/**
 * Component for displaying live traces in a hierarchical graph visualization.
 * Uses ngx-vflow and d3-hierarchy to create a horizontal tree layout showing:
 * - External User (root) → Services → Operations (left to right)
 * - Live traces grouped by service and operation
 * - Animated edges showing data flow
 *
 * Uses LiveTracesManagerService for connection, filtering, and deduplication logic.
 */
@Component({
  selector: "app-live-traces-graph",
  standalone: true,
  imports: [CommonModule, LiveTracesControlsComponent, Vflow],
  providers: [LiveTracesManagerService],
  templateUrl: "./live-traces-graph.component.html",
  styleUrls: ["./live-traces-graph.component.css"],
})
export class LiveTracesGraphComponent
  implements OnInit, AfterViewInit, OnDestroy
{
  @Input() set initialServiceName(value: string) {
    if (value) this.manager.serviceName = value;
  }
  @Input() set initialOperationName(value: string) {
    if (value) this.manager.operationName = value;
  }
  @Input() autoConnect = false;
  @Input() set maxItems(value: number) {
    this.manager.maxItems = value;
  }
  @Input() allowCustomization = false;

  vflow = viewChild(VflowComponent);

  // Graph nodes and edges for ngx-vflow
  protected nodes: DynamicNode[] = [];
  protected edges: Edge[] = [];

  // Layout parameters for horizontal tree
  private baseRadius = 300; // horizontal distance between levels
  private opRingRadius = 150; // additional horizontal spacing for operations

  // Track if initial fitView has been done
  private hasInitiallyFitView = false;

  // Map of service::operation to Observable of traces
  private traceMap$: Observable<Map<string, ReadableSpan[][]>> = new Subject();

  // Track known service-operation combinations to detect new ones
  private knownServiceOperations = new Set<string>();

  // Observable properties for template bindings
  protected totalServices$!: Observable<number>;
  protected totalOperations$!: Observable<number>;

  private subscription?: Subscription;

  // Injected services
  public manager = inject(LiveTracesManagerService);
  private route = inject(ActivatedRoute);

  ngOnInit(): void {
    // Check if allowCustomization is set in route data
    this.route.data.subscribe((data) => {
      if (data["allowCustomization"] !== undefined) {
        this.allowCustomization = data["allowCustomization"];
      }
    });

    if (this.autoConnect) {
      this.manager.connect();
    }
  }

  ngAfterViewInit(): void {
    this.traceMap$ = this.manager.traces$.pipe(
      map((traces) => {
        // Build service-operation mapping from traces
        return this.buildServiceOperationTraceMap(traces);
      }),
    );

    // Initialize observable properties for template
    this.totalServices$ = this.traceMap$.pipe(
      map((tracesMap) => {
        const services = new Set<string>();
        tracesMap.forEach((__, key) => {
          const [service] = key.split("::");
          services.add(service);
        });
        return services.size;
      }),
    );

    this.totalOperations$ = this.traceMap$.pipe(
      map((tracesMap) => tracesMap.size),
    );

    // Subscribe to trace map updates to rebuild graph only when new operations appear
    this.subscription = this.traceMap$.subscribe((tracesMap) => {
      const hasNewOperations = this.checkForNewOperations(tracesMap);
      if (hasNewOperations) {
        console.log("New service-operation detected, rebuilding graph...");
        this.rebuildGraph(tracesMap);
      }
    });
  }

  ngOnDestroy(): void {
    if (this.subscription) {
      this.subscription.unsubscribe();
    }
  }

  /**
   * Connect to live traces and clear graph state.
   */
  connect(): void {
    // Clear graph-specific state before connecting
    this.nodes = [];
    this.edges = [];
    this.hasInitiallyFitView = false;
    this.knownServiceOperations.clear();

    // Connect to trace stream
    this.manager.connect();
  }

  /**
   * Clear graph state.
   */
  clear(): void {
    this.manager.clear();
    this.nodes = [];
    this.edges = [];
    this.hasInitiallyFitView = false;
    this.knownServiceOperations.clear();
  }

  /**
   * Check if the traces map contains any new service-operation combinations.
   * @param tracesMap Current map of service-operation to traces
   * @returns true if new operations were found, false otherwise
   */
  private checkForNewOperations(
    tracesMap: Map<string, ReadableSpan[][]>,
  ): boolean {
    let hasNewOperations = false;

    tracesMap.forEach((_, key) => {
      if (!this.knownServiceOperations.has(key)) {
        this.knownServiceOperations.add(key);
        hasNewOperations = true;
      }
    });

    return hasNewOperations;
  }

  /**
   * Rebuild the entire graph based on current service/operation mapping.
   */
  private rebuildGraph(tracesMap: Map<string, ReadableSpan[][]>): void {
    const { nodes, edges } = this.buildClusterGraph(tracesMap);
    this.nodes = nodes;
    this.edges = edges;

    // Fit view whenever graph is rebuilt (initial load or new operations)
    const hasFinite = this.nodes.every(
      (n) => Number.isFinite(n.point().x) && Number.isFinite(n.point().y),
    );
    if (this.nodes.length > 1 && hasFinite) {
      setTimeout(() => {
        const vf = this.vflow();
        if (vf) {
          vf.fitView({ duration: 300 });
          this.hasInitiallyFitView = true;
        }
      }, 200);
    }
  }

  /**
   * Build a hierarchical horizontal tree layout using d3-hierarchy.
   * Structure: External User → Services → Operations (left to right)
   * @param tracesMap Map of service-operation keys to trace arrays
   */
  private buildClusterGraph(tracesMap: Map<string, ReadableSpan[][]>): {
    nodes: DynamicNode[];
    edges: Edge[];
  } {
    // Build hierarchical data structure from tracesMap
    const children: HierarchyNodeData[] = [];

    tracesMap.forEach((_, key) => {
      const [serviceName, operationName] = key.split("::");

      let serviceNode = children.find((c) => c.id === `svc:${serviceName}`);
      if (!serviceNode) {
        serviceNode = {
          id: `svc:${serviceName}`,
          kind: "service",
          label: serviceName,
          children: [],
        };
        children.push(serviceNode);
      }

      if (!serviceNode.children) {
        serviceNode.children = [];
      }
      serviceNode.children.push({
        id: `op:${serviceName}:${operationName}`,
        kind: "operation",
        label: operationName,
        serviceName: serviceName,
      });
    });

    const data: HierarchyNodeData = {
      id: "external-user",
      kind: "app",
      label: "External User",
      type: ExternalUserNodeComponent,
      children: children,
      clientIpFilter: this.manager.clientIpFilter,
    };

    // Create d3 hierarchy
    const root = d3h.hierarchy(data);

    // Calculate tree dimensions (horizontal layout)
    const treeWidth = this.baseRadius + this.opRingRadius;
    const treeHeight = Math.max(800, tracesMap.size * 90); // Vertical spread
    const tree = d3h
      .tree<any>()
      .size([treeHeight, treeWidth])
      .separation((a, b) => {
        // More space between operations of different services
        return a.parent == b.parent ? 1 : 2;
      });

    tree(root);

    const nodes: ComponentDynamicNode[] = [];
    const edges: Edge[] = [];

    // Use Cartesian coordinates directly (horizontal tree to the right)
    root.descendants().forEach((nd) => {
      // d3.tree sets x (vertical position) and y (horizontal position from root)
      const x = (nd.y as number) || 0; // Horizontal: left to right
      const y = (nd.x as number) || 0; // Vertical: top to bottom

      const id: string = nd.data.id;
      const kind: "app" | "service" | "operation" = nd.data.kind;
      const label: string = nd.data.label;

      const payload: Record<string, unknown> = { label, kind };

      // Add client IP filter for external user node
      if (kind === "app" && nd.data.clientIpFilter) {
        payload["clientIpFilter"] = nd.data.clientIpFilter;
      }

      if (kind === "operation" && nd.data.serviceName) {
        payload["serviceName"] = nd.data.serviceName;
        payload["operationName"] = nd.data.label;
        // Get the filtered observable for this service-operation
        const traces$ = this.getTracesForServiceOperation(
          nd.data.serviceName,
          nd.data.label,
        );
        if (traces$) {
          payload["traces$"] = traces$;
        }
      }

      nodes.push({
        id,
        point: signal({ x, y }),
        type: this.nodeFactory(kind),
        data: signal(payload),
        draggable: signal(true),
      });
    });

    // Build edges from hierarchy links
    root.links().forEach((lnk) => {
      const sid = (lnk.source.data as HierarchyNodeData).id;
      const tid = (lnk.target.data as HierarchyNodeData).id;
      edges.push({
        id: `${sid}->${tid}`,
        source: sid,
        target: tid,
        type: "template",
      });
    });

    return { nodes, edges };
  }

  /**
   * Factory method to create appropriate node component based on kind.
   */
  private nodeFactory(kind: string) {
    switch (kind) {
      case "app":
        return ExternalUserNodeComponent;
      case "service":
        return ServiceNodeComponent;
      case "operation":
        return OperationNodeComponent;
      default:
        throw new Error(`Unknown node kind: ${kind}`);
    }
  }

  /**
   * Build a map of service-operation combinations to their corresponding traces.
   * Groups traces by extracting service and operation names from span attributes.
   * @param traces Array of trace spans to group
   * @returns Map with keys in format "service::operation" pointing to arrays of traces
   */
  private buildServiceOperationTraceMap(
    traces: ReadableSpan[][],
  ): Map<string, ReadableSpan[][]> {
    if (!traces || traces.length === 0) return new Map();

    const tracesMap = new Map<string, ReadableSpan[][]>();

    traces.forEach((trace) => {
      // Extract unique service-operation combinations from the trace
      let service = "unknown";
      let operation = "unknown";

      for (const span of trace) {
        const attrs = span.attributes || {};
        service = (attrs["service.name"] as string) || service;
        operation = (attrs["operation.name"] as string) || operation;
        if (service !== "unknown" && operation !== "unknown") {
          break; // Found valid service and operation
        }
      }
      const key = `${service}::${operation}`;
      if (!tracesMap.has(key)) {
        tracesMap.set(key, []);
      }
      tracesMap.get(key)?.push(trace);
    });
    return tracesMap;
  }

  /**
   * Get an observable of traces filtered by service and operation.
   * @param service The service name to filter by
   * @param operation The operation name to filter by
   * @returns Observable of filtered traces, or null if not found
   */
  getTracesForServiceOperation(
    service: string,
    operation: string,
  ): Observable<ReadableSpan[][]> | null {
    const key = `${service}::${operation}`;
    return this.traceMap$.pipe(map((tracesMap) => tracesMap.get(key) || []));
  }
}
