import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { finalize } from 'rxjs';
import { AuthService } from './auth.service';

@Component({
  selector: 'app-auth-page',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './auth-page.html',
  styleUrl: './auth-page.scss'
})
export class AuthPage {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly fb = inject(FormBuilder);
  readonly registering = this.route.snapshot.data['registering'] === true;
  readonly expired = this.route.snapshot.queryParamMap.get('expired') === '1';
  readonly busy = signal(false);
  readonly error = signal('');
  readonly showPassword = signal(false);
  readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email, Validators.maxLength(255)]],
    password: ['', [Validators.required, Validators.maxLength(72),
      ...(this.registering ? [Validators.minLength(8)] : [])]],
    firstName: ['', this.registering ? [Validators.required, Validators.pattern(/\S/), Validators.maxLength(100)] : []],
    lastName: ['', this.registering ? [Validators.required, Validators.pattern(/\S/), Validators.maxLength(100)] : []],
    phone: ['', [Validators.pattern(/^$|^[+0-9][0-9 .()\-]{5,29}$/)]],
    confirmPassword: ['']
  }, { validators: control => this.registering &&
    control.get('password')?.value !== control.get('confirmPassword')?.value ? { passwordMismatch: true } : null });

  invalid(name: keyof typeof this.form.controls): boolean {
    const control = this.form.controls[name];
    return control.touched && control.invalid;
  }
  submit(): void {
    if (this.busy()) return;
    this.form.markAllAsTouched();
    if (this.form.invalid) return;
    this.error.set('');
    this.busy.set(true);
    const values = this.form.getRawValue();
    const data = { email: values.email.trim(), password: values.password, firstName: values.firstName.trim(),
      lastName: values.lastName.trim(), phone: values.phone.trim() };
    const request = this.registering ? this.auth.register(data) :
      this.auth.login({ email: data.email, password: data.password });
    request.pipe(finalize(() => this.busy.set(false))).subscribe({
      next: () => void this.router.navigateByUrl('/account'),
      error: (error: HttpErrorResponse) => this.error.set(error.status === 0
        ? 'Il servizio non è raggiungibile. Riprova tra poco.'
        : error.error?.message || 'Non è stato possibile completare la richiesta. Riprova.')
    });
  }
}
