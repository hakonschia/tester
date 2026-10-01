'use client'

import React, {use, useEffect, useState} from "react";
import DefaultPage from "@/components/DefaultPage";
import useWebSocket, {ReadyState} from "react-use-websocket";
import {WEB_SOCKET_BASE_URL, WebSocketMessage} from "@/websocket/WebSocket";

export default function Page({params}: {
    params: Promise<{ id: string }>;
}) {
    const {id} = use(params);
    const [messages, setMessages] = useState<string[]>([])
    const {lastMessage, sendMessage, readyState} = useWebSocket(WEB_SOCKET_BASE_URL + "devices")

    useEffect(() => {
        if (readyState == ReadyState.OPEN) {
            sendMessage(JSON.stringify({ type: "subscribe-to-device-updates", data: id }))
        }
    }, [readyState])

    useEffect(() => {
        if (lastMessage === null) return

        const socketMessage = JSON.parse(lastMessage.data) as WebSocketMessage
        console.log(socketMessage)

        if (socketMessage.type == "new-msg-from-device") {
            setMessages([...messages, socketMessage.data])
        }
    }, [lastMessage])

    function requestDevice() {
        sendMessage(JSON.stringify({ type: "request-device", data: "" }))
    }

    function free() {
        sendMessage(JSON.stringify({ type: "free-device", data: id }))
    }

    return (
        <DefaultPage>
            <p
                style={{
                    fontSize: "4em"
                }}
                onClick={requestDevice}
            >
                Request {id}
            </p>

            <p
                style={{
                    fontSize: "4em"
                }}
                onClick={free}
            >
                Free {id}
            </p>

            <ul
                style={{
                    gap: "8px",
                    display: "flex",
                    flexDirection: "column"
                }}
            >
                {messages.map((message, index) => (
                    <p key={index}>{message}</p>
                ))}
            </ul>
        </DefaultPage>
    )
}
