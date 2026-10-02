class Device {
    serial: String
    manufacturer: String
    model: String
    taken: Boolean

    constructor(serial: String, manufacturer: String, model: String, taken: Boolean) {
        this.serial = serial
        this.manufacturer = manufacturer
        this.model = model
        this.taken = taken
    }
}