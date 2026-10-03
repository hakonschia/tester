export interface Device {
    serial: String
    manufacturer: String
    model: String
}

export interface DeviceStatus {
    device: Device;
    taken: Boolean;
    currentTestStatus: CurrentTestStatus;
}

export interface FinishedTest {
    name: string;
    succeed: boolean;
}

export interface RunningTests {
    totalTests: number;
    finishedTests: FinishedTest[];
    currentlyRunningTest: string | null;
    previousRuns: RunningTests[];
}

export type CurrentTestStatus =
    | { type: 'com.hakonschia.tester.backend.DeviceStatus.CurrentTestStatus.NotRunningTests'; previousRuns: RunningTests[]; }
    | { type: 'com.hakonschia.tester.backend.DeviceStatus.CurrentTestStatus.RunningTests'; } & RunningTests

export interface DeviceStatus {
    device: Device;
    taken: boolean;
    currentTestStatus: CurrentTestStatus;
}
