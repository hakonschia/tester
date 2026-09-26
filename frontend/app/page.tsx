'use client'

import useWebSocket, {ReadyState} from 'react-use-websocket';
import React, {useEffect, useState} from "react";

class SocketMessage {
    type: string
    data: any

    constructor(type: string, data: any) {
        this.type = type
        this.data = data
    }
}

export default function Home() {
    const [socketUrl] = useState('ws://localhost:8080/socket/devices')
    const [messageHistory, setMessageHistory] = useState<String[]>([])

    const {sendMessage, lastMessage, readyState} = useWebSocket(
        socketUrl, {
            onOpen: () => {
                sendSocketMessage(new SocketMessage("init", null))
            }
        }
    )

    useEffect(() => {
        if (lastMessage != null) {
            setMessageHistory((prev) => [...prev, lastMessage.data])
        }
    }, [lastMessage])

    function sendSocketMessage(message: SocketMessage) {
        sendMessage(JSON.stringify(message))
    }

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

            <button
                onClick={() => sendSocketMessage(new SocketMessage('msg', "request-device"))}
                disabled={readyState !== ReadyState.OPEN}>
                Send "Hello World"
            </button>

            <ul>
                {messageHistory.map((message, index) => (
                    <li key={index}>{message}</li>
                ))}
            </ul>
        </div>
    );
}
