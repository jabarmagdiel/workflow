import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { NotificationsComponent } from './shared/notifications/notifications.component';

@Component({
    selector: 'app-root',
    standalone: true,
    imports: [RouterOutlet, NotificationsComponent],
    templateUrl: './app.component.html'
})
export class AppComponent { }
