'use client'

import React, {useState} from "react";
import Link from "next/link";

export default function DeviceListItem(
    { device }: { device: DeviceStatus }
) {
    const [isHovered, setIsHovered] = useState(false);

    return (
        <div
            onMouseEnter={() => setIsHovered(true)}
            onMouseLeave={() => setIsHovered(false)}

            style = {{
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
                    <p>
                        {device.device.model} ({device.device.serial}) is currently running tests
                    </p>
                    :
                    <p>
                        {device.device.model} ({device.device.serial}) is available
                    </p>
                }
            </Link>
        </div>
    )
}