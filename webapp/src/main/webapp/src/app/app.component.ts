import { Component, Provider } from '@angular/core';
import { RouterOutlet } from '@angular/router';

import { VerticalNavComponent } from './components/vertical-nav/vertical-nav.component';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, VerticalNavComponent],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css'
})
export class AppComponent {
  title = 'webapp-new';
}
