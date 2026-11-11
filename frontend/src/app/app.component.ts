import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { SECTIONS } from './categories';
import { UserStore } from './document.service';

/** App shell: sidebar navigation + <router-outlet> where each page is rendered. */
@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, FormsModule],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css'
})
export class AppComponent {
  readonly user = inject(UserStore);
  readonly sections = SECTIONS;
  userInput = this.user.userId();
  menuOpen = false;

  switchUser(): void {
    if (this.userInput.trim()) {
      this.user.set(this.userInput);   // pages react to the user signal and reload
    }
  }
}
