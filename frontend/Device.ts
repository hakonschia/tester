export class Device {
    serial: String
    manufacturer: String
    model: String

    constructor(serial: String, manufacturer: String, model: String) {
        this.serial = serial
        this.manufacturer = manufacturer
        this.model = model
        this.taken = taken
    }
}

export class DeviceStatus {
    device: Device
    taken: Boolean
    currentTestStatus: CurrentTestStatus

    constructor(device: Device, taken: Boolean, currentTestStatus: CurrentTestStatus) {
        this.device = device
        this.taken = taken
        this.currentTestStatus = currentTestStatus
    }
}

export interface FinishedTest {
    name: string;
    succeed: boolean;
}

export interface RunningTests {
    totalTests: number;
    finishedTests: FinishedTest[];
    currentlyRunningTest: string | null;
}

export type CurrentTestStatus =
    | { type: 'com.hakonschia.tester.backend.DeviceStatus.CurrentTestStatus.NotRunningTests'; previousRuns: RunningTestsDetails[]; }
    | { type: 'com.hakonschia.tester.backend.DeviceStatus.CurrentTestStatus.RunningTests'; } & RunningTests

export interface DeviceStatus {
    device: Device;
    taken: boolean;
    currentTestStatus: CurrentTestStatus;
}
