import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { ImportersPageComponent } from './importers.page';

describe('ServicesPageComponent', () => {
  let component: ImportersPageComponent;
  let fixture: ComponentFixture<ImportersPageComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ ImportersPageComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(ImportersPageComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});