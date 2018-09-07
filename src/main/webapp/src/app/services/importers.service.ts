import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

import { ImportJob } from '../models/importer.model';


@Injectable({ providedIn: 'root' })
export class ImportersService {

  private rootUrl: string = '/api';

  constructor(private http: HttpClient) { }

  getImportJobs(page: number = 1, pageSize: number = 20): Observable<ImportJob[]> {
    const options = { params: new HttpParams().set('page', String(page - 1)).set('size', String(pageSize)) };
    return this.http.get<ImportJob[]>(this.rootUrl + '/jobs', options);
  }

  filterImportJobs(filter: string): Observable<ImportJob[]> {
    const options = { params: new HttpParams().set('name', filter) };
    return this.http.get<ImportJob[]>(this.rootUrl + '/jobs', options);
  }

  countImportJobs(): Observable<any> { 
    return this.http.get<any>(this.rootUrl + '/jobs/count');
  }

  createImportJob(job: ImportJob): Observable<ImportJob> {
    return this.http.post<ImportJob>(this.rootUrl + '/jobs', job);
  }

  updateImportJob(job: ImportJob): Observable<ImportJob> {
    return this.http.post<ImportJob>(this.rootUrl + '/jobs/' + job.id, job);
  }

  deleteImportJob(job: ImportJob): Observable<ImportJob> {
    return this.http.delete<ImportJob>(this.rootUrl + '/jobs/' + job.id);
  }

  activateImportJob(job: ImportJob): Observable<ImportJob> {
    return this.http.put<ImportJob>(this.rootUrl + '/jobs/' + job.id + '/activate', job);
  }

  startImportJob(job: ImportJob): Observable<ImportJob> {
    return this.http.put<ImportJob>(this.rootUrl + '/jobs/' + job.id + '/start', job);
  }

  stopImportJob(job: ImportJob): Observable<ImportJob> {
    return this.http.put<ImportJob>(this.rootUrl + '/jobs/' + job.id + '/stop', job);
  }
}