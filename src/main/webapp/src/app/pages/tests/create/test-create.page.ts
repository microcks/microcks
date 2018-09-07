import { Component, OnInit} from "@angular/core";
import { ActivatedRoute, Router, ParamMap } from "@angular/router";

import { Observable } from 'rxjs';
import { switchMap } from 'rxjs/operators';

import { Notification, NotificationEvent, NotificationService, NotificationType } from 'patternfly-ng/notification';

import { ServicesService } from '../../../services/services.service';
import { TestsService } from '../../../services/tests.service';
import { Service } from '../../../models/service.model';
import { TestRunnerType } from "../../../models/test.model";

@Component({
  selector: "test-create-page",
  templateUrl: "test-create.page.html",
  styleUrls: ["test-create.page.css"]
})
export class TestCreatePageComponent implements OnInit {

  service: Observable<Service>;
  serviceId: string;
  testEndpoint: string;
  runnerType: TestRunnerType;
  submitEnabled: boolean = false;
  notifications: Notification[];

  constructor(private servicesSvc: ServicesService, public testsSvc: TestsService, private notificationService: NotificationService,
    private route: ActivatedRoute, private router: Router) {
  }

  ngOnInit() {
    this.notifications = this.notificationService.getNotifications();
    this.service = this.route.paramMap.pipe(
      switchMap((params: ParamMap) => {
        // (+) before `params.get()` turns the string into a number
        this.serviceId = params.get('serviceId');
        return this.servicesSvc.getService(this.serviceId);
      })
    );
  }

  public checkForm(): void {
    this.submitEnabled = (this.testEndpoint !== undefined && this.testEndpoint.length > 0 && this.runnerType !== undefined);
    console.log("submitEnabled: " + this.submitEnabled);
  }

  public cancel(): void {
    this.router.navigate(['/services', this.serviceId]);
  }

  public createTest(): void {
    var test = {serviceId: this.serviceId, testEndpoint: this.testEndpoint, runnerType: this.runnerType};
    console.log("[createTest] test: " + JSON.stringify(test));
    this.testsSvc.create(test).subscribe(
      {
        next: res => {
          this.notificationService.message(NotificationType.SUCCESS,
              String(res.testNumber), "Test #" + res.testNumber + " has been launched", false, null, null);
          this.router.navigate(['/tests/runner', res.id]);
        },
        error: err => {
          this.notificationService.message(NotificationType.DANGER,
              "New test", "New test cannot be launched (" + err.message + ")", false, null, null);
        },
        complete: () => console.log('Observer got a complete notification')
      }
    );
  }
}