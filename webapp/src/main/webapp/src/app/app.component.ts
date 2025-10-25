import { Component, Provider, OnInit, OnDestroy } from '@angular/core';
import { RouterOutlet } from '@angular/router';

import { VerticalNavComponent } from './components/vertical-nav/vertical-nav.component';
import { DragDropOverlayComponent } from './components/drag-drop-overlay/drag-drop-overlay.component';
import { DragDropService } from './services/drag-drop.service';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, VerticalNavComponent, DragDropOverlayComponent],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css'
})
export class AppComponent implements OnDestroy {
  title = 'webapp-new';

  constructor(private dragDropService: DragDropService) {}

  ngOnDestroy(): void {
    this.dragDropService.destroy();
  }
}
