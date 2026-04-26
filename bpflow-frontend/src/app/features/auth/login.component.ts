import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../core/services/auth.service';
import { Router } from '@angular/router';

@Component({
    selector: 'app-login',
    standalone: true,
    imports: [FormsModule],
    templateUrl: './login.component.html'
})
export class LoginComponent {
    auth = inject(AuthService);
    router = inject(Router);

    email = '';
    password = '';
    loading = false;

    onLogin() {
        this.loading = true;
        this.auth.login({ email: this.email, password: this.password }).subscribe({
            next: () => this.router.navigate(['/dashboard']),
            error: () => {
                this.loading = false;
                alert('Credenciales inválidas');
            }
        });
    }
}
