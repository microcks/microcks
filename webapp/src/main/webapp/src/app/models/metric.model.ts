/*
 * Copyright The Microcks Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
export type DailyInvocations = {
  id: string;
  day: string;
  serviceName: string;
  serviceVersion: string;
  dailyCount: number;

  hourlyCount: { string: number };
  minuteCount: { string: number };
};

export type TestConformanceMetric = {
  id: string;
  serviceId: string;
  aggregationLabelValue: string;
  maxPossibleScore: number;
  currentScore: number;
  lastUpdateDay: string;
  latestTrend: Trend;

  latestScores: { string: number };
};
export enum Trend {
  DOWN = "DOWN",
  LOW_DOWN = "LOW_DOWN",
  STABLE = "STABLE",
  LOW_UP = "LOW_UP",
  UP = "UP",
}

export type WeightedMetricValue = {
  name: string;
  weight: number;
  value: number;
};

export type TestResultSummary = {
  id: string;
  testDate: number;
  serviceId: string;
  success: boolean;
};