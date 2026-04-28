import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { UserService, User } from '../../core/services/user.service';

@Component({
  selector: 'app-users',
  standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule],
  templateUrl: './users.component.html',
  styleUrls: ['./users.component.css']
})
export class UsersComponent implements OnInit {
  private userService = inject(UserService);
  private fb = inject(FormBuilder);

  users = signal<User[]>([]);
  loading = signal<boolean>(true);
  error = signal<string | null>(null);

  isModalOpen = signal<boolean>(false);
  modalMode = signal<'CREATE' | 'EDIT'>('CREATE');
  selectedUserId = signal<string | null>(null);

  userForm: FormGroup = this.fb.group({
    email: ['', [Validators.required, Validators.email]],
    password: [''],
    firstName: ['', Validators.required],
    lastName: ['', Validators.required],
    phone: [''],
    department: [''],
    position: [''],
    roles: [[]] // To easily handle multiple selection
  });

  availableRoles = ['ADMIN', 'MANAGER', 'OFFICER', 'CLIENT', 'DESIGNER'];

  ngOnInit(): void {
    this.loadUsers();
  }

  loadUsers(): void {
    this.loading.set(true);
    this.error.set(null);
    this.userService.getUsers().subscribe({
      next: (data) => {
        this.users.set(data);
        this.loading.set(false);
      },
      error: (err) => {
        console.error('Error loading users:', err);
        this.error.set('Could not load users. Make sure you have admin privileges.');
        this.loading.set(false);
      }
    });
  }

  openCreateModal(): void {
    this.modalMode.set('CREATE');
    this.selectedUserId.set(null);
    this.userForm.reset({ roles: ['USER'] });
    this.userForm.get('password')?.setValidators([Validators.required, Validators.minLength(8)]);
    this.userForm.get('password')?.updateValueAndValidity();
    this.isModalOpen.set(true);
  }

  openEditModal(user: User): void {
    this.modalMode.set('EDIT');
    if (user.id) this.selectedUserId.set(user.id);
    
    this.userForm.patchValue({
      email: user.email,
      firstName: user.firstName,
      lastName: user.lastName,
      phone: user.phone,
      department: user.department,
      position: user.position,
      roles: user.roles || []
    });

    this.userForm.get('password')?.clearValidators();
    this.userForm.get('password')?.updateValueAndValidity();
    this.isModalOpen.set(true);
  }

  closeModal(): void {
    this.isModalOpen.set(false);
  }

  toggleRoleSelection(role: string): void {
    const currentRoles = this.userForm.get('roles')?.value as string[];
    let newRoles = [...(currentRoles || [])];
    
    if (newRoles.includes(role)) {
      newRoles = newRoles.filter(r => r !== role);
    } else {
      newRoles.push(role);
    }
    
    // Ensure at least one role is selected usually, but for now just update
    if (newRoles.length === 0) newRoles = ['USER'];
    this.userForm.get('roles')?.setValue(newRoles);
  }

  isRoleSelected(role: string): boolean {
    const roles = this.userForm.get('roles')?.value || [];
    return roles.includes(role);
  }

  saveUser(): void {
    if (this.userForm.invalid) {
      Object.keys(this.userForm.controls).forEach(key => {
        this.userForm.get(key)?.markAsTouched();
      });
      return;
    }

    const formData = this.userForm.value;

    if (this.modalMode() === 'CREATE') {
      const newUser = {
        email: formData.email,
        password: formData.password,
        firstName: formData.firstName,
        lastName: formData.lastName,
        phone: formData.phone,
        department: formData.department,
        position: formData.position
      };

      this.userService.createUser(newUser).subscribe({
        next: (createdUser) => {
          // After creating the user, assign roles
          const userId = createdUser.user?.id;
          if (userId && formData.roles.length > 0) {
             this.userService.updateUserRoles(userId, formData.roles).subscribe(() => this.loadUsers());
          } else {
             this.loadUsers();
          }
          this.closeModal();
        },
        error: (err) => alert('Error creating user: ' + (err.error?.message || err.message))
      });
    } else {
      const id = this.selectedUserId();
      if (!id) return;
      
      const updateData = {
        firstName: formData.firstName,
        lastName: formData.lastName,
        phone: formData.phone,
        department: formData.department,
        position: formData.position
      };

      this.userService.updateUser(id, updateData).subscribe({
        next: () => {
          // Update roles
          this.userService.updateUserRoles(id, formData.roles).subscribe({
            next: () => {
              this.loadUsers();
              this.closeModal();
            },
            error: (err) => alert('Error updating roles: ' + err.message)
          });
        },
        error: (err) => alert('Error updating user: ' + err.message)
      });
    }
  }

  toggleStatus(user: User): void {
    if (!user.id) return;
    this.userService.toggleUserStatus(user.id).subscribe({
      next: () => {
        this.loadUsers();
      },
      error: (err) => alert('Error toggling status')
    });
  }

  deleteUser(user: User): void {
    if (!user.id) return;
    if (confirm(`Are you sure you want to delete ${user.firstName} ${user.lastName}?`)) {
      this.userService.deleteUser(user.id).subscribe({
        next: () => this.loadUsers(),
        error: () => alert('Error deleting user')
      });
    }
  }
}
