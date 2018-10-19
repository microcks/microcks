export class DailyInvocations {
  id: string;
  day: string;
  serviceName: string;
  serviceVersion: string;
  dailyCount: number;

  hourlyCount: {string, number};
  minuteCount: {string, number};
}