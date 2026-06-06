import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { UrlService, ShortUrlResponse } from '../../services/url.service';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './dashboard.component.html',
  styleUrls: ['./dashboard.component.css']
})
export class DashboardComponent implements OnInit {
  longUrl = '';
  username = '';
  history: ShortUrlResponse[] = [];
  
  isLoading = false;
  errorMessage = '';
  successMessage = '';
  
  copiedIndex: number | null = null;

  constructor(
    private authService: AuthService,
    private urlService: UrlService,
    private router: Router
  ) {
    this.username = this.authService.getUsername() || 'User';
  }

  ngOnInit(): void {
    this.loadHistory();
  }

  loadHistory(): void {
    this.urlService.getHistory().subscribe({
      next: (data) => {
        // Sort by code length or simple order. Since shortcodes are random, sorting by date is best, 
        // but since we return database records, they are already grouped.
        this.history = data;
      },
      error: (err) => {
        this.errorMessage = 'Failed to load link history';
      }
    });
  }

  onSubmit(): void {
    if (!this.longUrl) {
      this.errorMessage = 'Please enter a valid URL';
      return;
    }

    this.isLoading = true;
    this.errorMessage = '';
    this.successMessage = '';

    this.urlService.shortenUrl(this.longUrl).subscribe({
      next: (response) => {
        this.isLoading = false;
        this.longUrl = '';
        this.loadHistory();
        this.successMessage = 'URL shortened successfully!';
      },
      error: (err) => {
        this.isLoading = false;
        this.errorMessage = err.error?.message || 'Failed to shorten URL. Make sure it is a valid absolute link (e.g. http://...)';
      }
    });
  }

  copyToClipboard(text: string, index: number): void {
    navigator.clipboard.writeText(text).then(() => {
      this.copiedIndex = index;
      setTimeout(() => {
        if (this.copiedIndex === index) {
          this.copiedIndex = null;
        }
      }, 2000);
    });
  }

  logout(): void {
    this.authService.logout();
    this.router.navigate(['/login']);
  }
}
