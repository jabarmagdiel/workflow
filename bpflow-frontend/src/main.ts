import { bootstrapApplication } from '@angular/platform-browser';
import { provideRouter, Routes, withComponentInputBinding } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideAnimations } from '@angular/platform-browser/animations';
import { authGuard } from './app/core/guards/auth.guard';
import { authInterceptor } from './app/core/interceptors/auth.interceptor';
import { AppComponent } from './app/app.component';

const routes: Routes = [
    { path: 'login', loadComponent: () => import('./app/features/auth/login.component').then(c => c.LoginComponent) },
    {
        path: '',
        loadComponent: () => import('./app/features/dashboard/dashboard-layout.component').then(c => c.DashboardLayoutComponent),
        canActivate: [authGuard],
        children: [
            { path: 'dashboard', loadComponent: () => import('./app/features/dashboard/dashboard-home.component').then(c => c.DashboardHomeComponent) },
            { path: 'workflows', loadComponent: () => import('./app/features/workflows/workflow-designer.component').then(c => c.WorkflowDesignerComponent) },
            { path: 'instances', loadComponent: () => import('./app/features/instances/instances.component').then(c => c.InstancesComponent) },
            { path: 'tasks', loadComponent: () => import('./app/features/tasks/my-tasks.component').then(c => c.MyTasksComponent) },
            { path: 'fraud', loadComponent: () => import('./app/features/fraud/fraud-dashboard.component').then(c => c.FraudDashboardComponent), data: { roles: ['ADMIN', 'MANAGER'] } },
            { path: 'bitacora', loadComponent: () => import('./app/features/bitacora/bitacora.component').then(c => c.BitacoraComponent), data: { roles: ['ADMIN', 'MANAGER'] } },
            { path: 'users', loadComponent: () => import('./app/features/users/users.component').then(c => c.UsersComponent), data: { roles: ['ADMIN'] } },
            { path: 'analytics', loadComponent: () => import('./app/features/analytics/bottlenecks.component').then(c => c.BottlenecksComponent), data: { roles: ['ADMIN', 'MANAGER'] } },
            { path: 'health', loadComponent: () => import('./app/features/admin/health.component').then(c => c.HealthComponent), data: { roles: ['ADMIN', 'MANAGER'] } },
            { path: '', redirectTo: 'dashboard', pathMatch: 'full' }
        ]
    },
    { path: '**', redirectTo: 'login' }
];

bootstrapApplication(AppComponent, {
    providers: [
        provideRouter(routes, withComponentInputBinding()),
        provideHttpClient(withInterceptors([authInterceptor])),
        provideAnimations()
    ]
}).catch(err => console.error(err));

