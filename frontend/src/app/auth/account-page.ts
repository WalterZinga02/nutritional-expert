import { Component, inject } from '@angular/core';
import { AuthService } from './auth.service';

@Component({
  selector: 'app-account-page',
  template: `
    <section class="account">
      <p class="eyebrow">IL TUO SPAZIO PERSONALE</p>
      <h1>Ciao, {{ auth.user()?.firstName }}.</h1>
      <p>Hai effettuato l'accesso a Nutritional Expert.</p>
      @if (auth.user(); as user) {
        <dl>
          <div><dt>Nome e cognome</dt><dd>{{ user.firstName }} {{ user.lastName }}</dd></div>
          <div><dt>Email</dt><dd>{{ user.email }}</dd></div>
          <div><dt>Telefono</dt><dd>{{ user.phone || 'Non indicato' }}</dd></div>
        </dl>
      }
      <button class="primary" type="button" (click)="auth.logout()">Esci dall'account</button>
    </section>
  `,
  styles: `
    .account { max-width:650px; margin:4rem auto; padding:0 1.5rem; }
    h1 { font:400 2.8rem Georgia,serif; margin:1rem 0; }
    p, dt { color:var(--muted); } dl { background:white; border:1px solid var(--line); border-radius:16px; padding:1.5rem; margin:2rem 0; }
    dl div + div { margin-top:1.3rem; } dt { font-size:.85rem; } dd { margin:.4rem 0 0; overflow-wrap:anywhere; }
  `
})
export class AccountPage { readonly auth = inject(AuthService); }
