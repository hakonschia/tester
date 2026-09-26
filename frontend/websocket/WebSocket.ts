export const WEB_SOCKET_BASE_URL = "ws://localhost:8080/socket/"

export class WebSocketMessage {
    type: string
    data: any

    constructor(type: string, data: any) {
        this.type = type
        this.data = data
    }
}
