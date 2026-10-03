'use client'

import React, {use, useEffect, useState} from "react";
import DefaultPage from "@/components/DefaultPage";
import useWebSocket, {ReadyState} from "react-use-websocket";
import {WEB_SOCKET_BASE_URL, WebSocketMessage} from "@/websocket/WebSocket";
import {FinishedTest, RunningTests} from "@/Device";

export default function Page({params}: {
    params: Promise<{ id: string }>;
}) {
    const {id} = use(params);
    const [messages, setMessages] = useState<string[]>([])
    const {lastMessage, sendMessage, readyState} = useWebSocket(WEB_SOCKET_BASE_URL + "devices")
    const [device, setDevice] = useState<DeviceStatus>(null)

    useEffect(() => {
        if (readyState == ReadyState.OPEN) {
            sendMessage(JSON.stringify({type: "subscribe-to-device-updates", data: id}))
        }
    }, [readyState])

    useEffect(() => {
        if (lastMessage === null) return

        const socketMessage = JSON.parse(lastMessage.data) as WebSocketMessage
        console.log(socketMessage)

        if (socketMessage.type == "new-msg-from-device") {
            setMessages([...messages, socketMessage.data.toString()])
        } else if (socketMessage.type == "device-status") {
            setDevice(socketMessage.data as DeviceStatus)
        }
    }, [lastMessage])

    function requestDevice() {
        sendMessage(JSON.stringify({type: "request-device", data: ""}))
    }

    function free() {
        sendMessage(JSON.stringify({type: "free-device", data: id}))
    }

    return (
        <DefaultPage>
            {device != null && (
                <p
                    style={{
                        fontSize: "4em",
                        color: device.taken ? "rgb(239 135 0)" : "green",
                    }}
                >
                    Device {device.device.serial}
                </p>
            )}

            <div
                style={{
                    display: "flex",
                    flexDirection: "row",
                    gap: "16px",
                }}
            >
                <p
                    style={{
                        fontSize: "1em"
                    }}
                    onClick={requestDevice}
                >
                    Request {id}
                </p>

                <p
                    style={{
                        fontSize: "1em"
                    }}
                    onClick={free}
                >
                    Free {id}
                </p>

            </div>

            {device != null && (
                <TestStatusView status={device.currentTestStatus}/>
            )}
        </DefaultPage>
    )
}

function TestStatusView({status}: { status: CurrentTestStatus }) {
    switch (status.type) {
        case "com.hakonschia.tester.backend.DeviceStatus.CurrentTestStatus.NotRunningTests":
            return <div>
                <p
                    style={{
                        fontSize: "2em"
                    }}
                >
                    Not currently running any tests
                </p>

                <PreviousRunsList runs={status.previousRuns}/>
            </div>

        case "com.hakonschia.tester.backend.DeviceStatus.CurrentTestStatus.RunningTests":
            return <div>
                <p>
                    Running tests: {status.finishedTests.length} / {status.totalTests}
                </p>

                {status.currentlyRunningTest != null && (
                    <p>Test running now: {status.currentlyRunningTest}</p>
                )}

                <ul
                    style={{
                        gap: "8px",
                        display: "flex",
                        flexDirection: "column"
                    }}
                >
                    {status.finishedTests.map((test, index) => (
                        <FinishedTestListItem key={index} test={test}/>
                    ))}
                </ul>

                <PreviousRunsList runs={status.previousRuns}/>
            </div>
    }
}

function PreviousRunsList({runs}: { runs: RunningTests[] }) {
    return (
        <div>
            {runs.length != 0 ?
                <div>
                    <div
                        style={{
                            height: "48px"
                        }}/>

                    <p
                        style={{
                            fontSize: "2em"
                        }}
                    >
                        See previous runs on this device ({runs.length}):
                    </p>

                    <div
                        style={{
                            height: "12px"
                        }}/>

                    <ul
                        style={{
                            gap: "16px",
                            display: "flex",
                            flexDirection: "column"
                        }}
                    >
                        {runs.map((run, index) => (
                            <PreviousTestRun key={index} testRun={run}/>
                        ))}
                    </ul>
                </div>
                :
                <p>
                    No previous runs
                </p>
            }
        </div>
    )
}

function PreviousTestRun({testRun}: { testRun: RunningTests }) {
    const [isExpanded, setIsExpanded] = useState(false)
    const [isHovered, setIsHovered] = useState(false)

    return (
        <div
            style={{
                gap: "8px",
                display: "block",
                padding: "8px",
                border: "2px",
                borderRadius: isHovered ? "16px" : "8px",
                background: isHovered ? "rgb(16 15 15)" : "black",
                transition: "ease-in-out 0.2s"
            }}
            onClick={() => setIsExpanded(!isExpanded)}
            onMouseEnter={() => setIsHovered(true)}
            onMouseLeave={() => setIsHovered(false)}
        >
            <p
                style={{
                    fontSize: "1.25em"
                }}
            >
                Run
            </p>

            {isExpanded && (
                <ul
                    style={{
                        gap: "8px",
                        display: "flex",
                        flexDirection: "column"
                    }}
                >
                    {testRun.finishedTests.map((test, index) => (
                        <FinishedTestListItem key={index} test={test}/>
                    ))}
                </ul>
            )}
        </div>
    )
}

function FinishedTestListItem({test}: { test: FinishedTest }) {
    switch (test.type) {
        case "com.hakonschia.tester.backend.DeviceStatus.CurrentTestStatus.RunningTests.FinishedTest.Success":
            return <p
                style={{
                    color: "green"
                }}
            >
                ✓ {test.name}
            </p>

        case "com.hakonschia.tester.backend.DeviceStatus.CurrentTestStatus.RunningTests.FinishedTest.Failed":
            const [isExpanded, setIsExpanded] = useState(false)

            return <p
                style={{
                    color: "red"
                }}
            >
                <p
                    // Amazing UX
                    onMouseEnter={() => setIsExpanded(true)}
                    onMouseLeave={() => setIsExpanded(false)}
                >
                    ✗ {test.name}
                </p>

                {isExpanded && (
                    <p>{test.stackTrace}</p>
                )}
            </p>
    }
}
