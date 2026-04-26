import { inject } from '@angular/core';
import { Router, CanActivateFn } from '@angular/router';
import { AuthService } from '../services/auth.service';

export const authGuard: CanActivateFn = (route, state) => {
    const authService = inject(AuthService);
    const router = inject(Router);

    if (!authService.isAuthenticated()) {
        router.navigate(['/login'], { queryParams: { returnUrl: state.url } });
        return false;
    }

    // Check role if specified in route data
    const requiredRoles = route.data['roles'] as string[];
    if (requiredRoles && !requiredRoles.some(role => authService.hasRole(role))) {
        router.navigate(['/access-denied']);
        return false;
    }

    return true;
};
