import {Children} from "react";
import Link from "next/link";
import {ReadyState} from "react-use-websocket";

// @ts-ignore
export default function DefaultPage({children, showHome, readyState}: {showHome: Boolean, readyState: ReadyState}) {
    const connectionStatus = {
        [ReadyState.CONNECTING]: 'Connecting',
        [ReadyState.OPEN]: 'Open',
        [ReadyState.CLOSING]: 'Closing',
        [ReadyState.CLOSED]: 'Closed',
        [ReadyState.UNINSTANTIATED]: 'Uninstantiated',
    }[readyState]

    return (
        <div>
            {showHome && (
                <Link
                    href={"/"}
                    style={{
                        position: "absolute",
                    }}
                >
                    Go home
                </Link>
            )}

            <div
                style={{
                    padding: "150px",
                    paddingTop: "50px",
                    gap: "16px",
                    display: "flex",
                    flexDirection: "column"
                }}
            >
                <p
                    style={{
                        fontSize: "4em"
                    }}
                >
                    Connection Status: {connectionStatus}
                </p>

                {children}
            </div>
        </div>
    )
}