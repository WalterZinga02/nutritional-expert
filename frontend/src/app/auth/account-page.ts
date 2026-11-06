/* Mostra il profilo corrente e gestisce il modulo di modifica di nome, cognome e telefono. */
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { finalize } from 'rxjs';
import { AuthService } from './auth.service';

@Component({
  selector: 'app-account-page',
  imports: [ReactiveFormsModule],
  templateUrl: './account-page.html',
  styleUrl: './account-page.scss'
})
export class AccountPage {
  readonly auth = inject(AuthService);
  private readonly fb = inject(FormBuilder);
  readonly editing = signal(false);
  readonly busy = signal(false);
  readonly error = signal('');
  readonly success = signal('');
  readonly form = this.fb.nonNullable.group({
    firstName: ['', [Validators.required, Validators.pattern(/\S/), Validators.maxLength(100)]],
    lastName: ['', [Validators.required, Validators.pattern(/\S/), Validators.maxLength(100)]],
    phone: ['', [Validators.pattern(/^$|^[+0-9][0-9 .()\-]{5,29}$/)]]
  });

  /** Precompila il modulo con il profilo corrente e apre la modifica. */
  startEditing(): void {
    const user = this.auth.user();
    if (!user || this.busy()) return;
    this.form.reset({ firstName: user.firstName, lastName: user.lastName, phone: user.phone ?? '' });
    this.error.set('');
    this.success.set('');
    this.editing.set(true);
  }
  /** Chiude la modifica senza inviare dati al backend. */
  cancel(): void {
    if (this.busy()) return;
    this.editing.set(false);
    this.error.set('');
  }
  /** Indica se un campo già toccato contiene errori. */
  invalid(name: keyof typeof this.form.controls): boolean {
    const control = this.form.controls[name];
    return control.touched && control.invalid;
  }
  /** Valida e invia il profilo; mostra conferma oppure errori generali e dei singoli campi. */
  save(): void {
    if (this.busy()) return;
    const value = this.form.getRawValue();
    const data = { firstName: value.firstName.trim(), lastName: value.lastName.trim(), phone: value.phone.trim() };
    this.form.patchValue(data);
    this.form.markAllAsTouched();
    if (this.form.invalid) return;
    this.busy.set(true);
    this.error.set('');
    this.success.set('');
    this.auth.updateProfile(data).pipe(finalize(() => this.busy.set(false))).subscribe({
      next: () => {
        this.editing.set(false);
        this.success.set('Profilo aggiornato correttamente.');
      },
      error: (error: HttpErrorResponse) => {
        this.error.set(error.status === 0 ? 'Il servizio non è raggiungibile. Riprova tra poco.'
          : error.error?.message || 'Non è stato possibile salvare il profilo. Riprova.');
        const fields = error.error?.errors;
        for (const name of ['firstName', 'lastName', 'phone'] as const) {
          if (typeof fields?.[name] === 'string') this.form.controls[name].setErrors({ server: fields[name] });
        }
      }
    });
  }
}
