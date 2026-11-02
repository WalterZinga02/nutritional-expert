import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { App } from './app';
describe('App', () => {
  it('renders the brand and account links', async () => {
    TestBed.configureTestingModule({ imports: [App], providers: [provideHttpClient(), provideRouter([])] });
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('Nutritional Expert');
    expect(fixture.nativeElement.querySelector('a[href="/registrazione"]')).toBeTruthy();
  });
});
