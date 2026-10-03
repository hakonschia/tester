'use client'

import React, {useState} from "react";
import Link from "next/link";
import {DeviceStatus, Device} from "@/Device";
import AnimatedBlock from "../components/AnimatedBlock"

export default function DeviceListItem(
    {device}: { device: DeviceStatus }
) {
    const [isHovered, setIsHovered] = useState(false);

    return (
        <div
            onMouseEnter={() => setIsHovered(true)}
            onMouseLeave={() => setIsHovered(false)}

            style={{
                gap: "8px",
                display: "block",
                padding: "8px",
                border: "2px",
                borderRadius: isHovered ? "16px" : "8px",
                background: isHovered ? "rgb(16 15 15)" : "black",
                transition: "ease-in-out 0.2s"
            }}
        >
            <Link
                href={`device/${device.device.serial}`}
                style={{
                    fontSize: "2em",
                    color: device.taken ? "rgb(239 135 0)" : "green",
                    display: "block"
                }}
            >
                {device.taken ?
                    <div
                        style={{
                            display: "flex",
                            flexDirection: "row",
                        }}
                    >
                        {`${device.device.model} (${device.device.serial}) ${
                            device.currentTestStatus.type == "com.hakonschia.tester.backend.DeviceStatus.CurrentTestStatus.RunningTests"
                                ? "is currently running tests"
                                : "is taken, but not running tests"
                        }`}
                    </div>
                    :
                    <p>
                        {device.device.model} ({device.device.serial}) is available
                    </p>
                }

                <AnimatedBlock show={isHovered}>
                    {device.currentTestStatus.type == "com.hakonschia.tester.backend.DeviceStatus.CurrentTestStatus.RunningTests" && (
                        <div>
                            <p>
                                {device.currentTestStatus.finishedTests.length} / {device.currentTestStatus.totalTests} finished
                            </p>

                            <p>
                                Current test running: {device.currentTestStatus.currentlyRunningTest}
                            </p>
                        </div>
                    )}
                </AnimatedBlock>
            </Link>
        </div>
    )
}