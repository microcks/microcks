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
import { async, ComponentFixture, TestBed } from "@angular/core/testing";

import { CommonModule } from "@angular/common";
import { HttpClientModule } from "@angular/common/http";
import { FormsModule } from "@angular/forms";
import { ActivatedRoute, Params, Router, RouterModule } from "@angular/router";
import { HighlightModule } from "ngx-highlightjs";
import {
  NotificationService,
  ToastNotificationListModule,
} from "patternfly-ng/notification";
import { of } from "rxjs";
import { Operation, Service } from "src/app/models/service.model";
import { ServicesService } from "src/app/services/services.service";
import { TestsService } from "src/app/services/tests.service";
import { TestCreatePageComponent } from "./test-create.page";

describe("ServicesPageComponent", () => {
  let component: TestCreatePageComponent;
  let fixture: ComponentFixture<TestCreatePageComponent>;
  let servicesSvcMock: jasmine.SpyObj<ServicesService>;
  let testsSvcMock: jasmine.SpyObj<TestsService>;
  let notificationsSvcMock: jasmine.SpyObj<NotificationService>;
  let activatedRouteMock: ActivatedRoute;
  let routerMock: jasmine.SpyObj<Router>;

  beforeEach(async(() => {
    servicesSvcMock = jasmine.createSpyObj("ServicesService", ["getService"]);
    testsSvcMock = jasmine.createSpyObj("TestsService", [
      "listByServiceId",
      "countryByServiceId",
    ]);
    notificationsSvcMock = jasmine.createSpyObj("NotificationService", [
      "addNotification",
      "getNotifications",
    ]);
    notificationsSvcMock.getNotifications.and.returnValue([]);
    activatedRouteMock = {
      paramMap: of({
        get: () => "mockServiceId",
        has: () => false,
      }) as Partial<Params> as Params,
    } as Partial<ActivatedRoute> as ActivatedRoute;

    TestBed.configureTestingModule({
      imports: [
        ToastNotificationListModule,
        RouterModule,
        FormsModule,
        HighlightModule,
        CommonModule,
        HttpClientModule,
      ],
      declarations: [TestCreatePageComponent],
      providers: [
        {
          provide: ServicesService,
          useValue: servicesSvcMock,
        },
        {
          provide: TestsService,
          useValue: testsSvcMock,
        },
        {
          provide: NotificationService,
          useValue: notificationsSvcMock,
        },
        {
          provide: ActivatedRoute,
          useValue: activatedRouteMock,
        },
        {
          provide: Router,
          useValue: routerMock,
        },
      ],
    }).compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(TestCreatePageComponent);
    component = fixture.componentInstance;
    servicesSvcMock.getService.and.returnValue(of(undefined));

    fixture.detectChanges();
  });

  it("should create", () => {
    expect(component).toBeTruthy();
    expect(component.removedOperationsNames).toEqual([]);
  });

  describe("on init", () => {
    beforeEach(async () => {
      notificationsSvcMock.getNotifications.and.returnValue([]);

      component.ngOnInit();
      await fixture.whenStable();
    });

    it("should define notifications", () => {
      notificationsSvcMock.getNotifications.and.returnValue([]);

      expect(component.notifications).toEqual([]);
    });
  });

  describe("resetOperations", () => {
    beforeEach(() => {
      component.removedOperationsNames = ["operationN"];
      component.resolvedService = {
        operations: [{ name: "operation1" }],
      } as Partial<Service> as Service;
    });

    [
      { input: [], expected: [] },
      {
        input: [{ name: "operation1" }, { name: "operation2" }],
        expected: ["operation1", "operation2"],
      },
      { input: undefined, expected: ["operation1"] },
    ].forEach((testCase) => {
      it(`should filter operations ${
        testCase.expected || "from resolvedService"
      } when input is ${JSON.stringify(testCase.input)}`, () => {
        component.resetOperations(
          testCase.input as Partial<Operation>[] as Operation[]
        );
        expect(component.removedOperationsNames).toEqual(testCase.expected);
      });
    });
  });
});
