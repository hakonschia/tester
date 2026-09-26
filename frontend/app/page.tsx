'use client'

import useWebSocket, {ReadyState} from 'react-use-websocket';
import React, {useEffect, useState} from "react";
import Link from "next/link";
import {WEB_SOCKET_BASE_URL, WebSocketMessage} from "@/websocket/WebSocket";
import DefaultPage from "@/components/DefaultPage";

export default function Home() {
    const [devices, setDevices] = useState<Device[]>([])
    const {lastMessage, readyState} = useWebSocket(WEB_SOCKET_BASE_URL + "devices")

    useEffect(() => {
        if (lastMessage === null) return

        const socketMessage = JSON.parse(lastMessage.data) as WebSocketMessage

        if (socketMessage.type == "all-devices") {
            setDevices(socketMessage.data as Device[])
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
        <DefaultPage>
            <p
                style={{
                    fontSize: "4em"
                }}
            >
                Connection Status: {connectionStatus}
            </p>

            {devices.length == 0 ? (
                <p>No devices found</p>
            ) : (
                <ul
                    style={{
                        gap: "8px",
                        display: "flex",
                        flexDirection: "column"
                    }}
                >
                    {devices.map((device, index) => (
                        <Link
                            key={index}
                            href={`device/${device.serial}`}
                            style={{
                                fontSize: "2em"
                            }}
                        >
                            See {device.model}-{device.manufacturer} ({device.serial})
                        </Link>
                    ))}
                </ul>
            )}
        </DefaultPage>

    );
}
