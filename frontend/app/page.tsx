'use client'

import useWebSocket, {ReadyState} from 'react-use-websocket';
import React, {useEffect, useState} from "react";
import Link from "next/link";
import {WEB_SOCKET_BASE_URL, WebSocketMessage} from "@/websocket/WebSocket";

export default function Home() {
    const [devices, setDevices] = useState<String[]>([])

    const {lastMessage, readyState} = useWebSocket(WEB_SOCKET_BASE_URL + "devices")

    useEffect(() => {
        if (lastMessage === null) return

        const socketMessage = JSON.parse(lastMessage.data) as WebSocketMessage
        console.log(lastMessage)

        if (socketMessage.type == "all-devices") {
            setDevices(socketMessage.data as string[])
        }
    }, [lastMessage])

    const connectionStatus = {
        [ReadyState.CONNECTING]: 'Connecting',
        [ReadyState.OPEN]: 'Open',
        [ReadyState.CLOSING]: 'Closing',
        [ReadyState.CLOSED]: 'Closed',
        [ReadyState.UNINSTANTIATED]: 'Uninstantiated',
    }[readyState]

    return (
        <div>
            <p>Connection Status: {connectionStatus}</p>

            <ul>
                {devices.map((device, index) => (
                    <Link key={index} href={`device/${device}`}>See {device}</Link>
                ))}
            </ul>
        </div>
    );
}
