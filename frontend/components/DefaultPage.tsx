import {Children} from "react";
import Link from "next/link";

// @ts-ignore
export default function DefaultPage({children, showHome}: {showHome: Boolean}) {
    return (
        <div>
            {showHome && (
                <Link
                    href={"/"}
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
                {children}
            </div>
        </div>
    )
}