import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface ShortUrlResponse {
  shortUrl: string;
  originalUrl: string;
  shortCode: string;
  clickCount: number;
}

@Injectable({
  providedIn: 'root'
})
export class UrlService {
  private apiUrl = 'http://localhost:8080/api/v1';

  constructor(private http: HttpClient) {}

  shortenUrl(url: string): Observable<ShortUrlResponse> {
    return this.http.post<ShortUrlResponse>(`${this.apiUrl}/shorten`, { url });
  }

  getHistory(): Observable<ShortUrlResponse[]> {
    return this.http.get<ShortUrlResponse[]>(`${this.apiUrl}/urls`);
  }
}
