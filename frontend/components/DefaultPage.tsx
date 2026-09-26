import {Children} from "react";

// @ts-ignore
export default function DefaultPage({children}) {
    return (
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
    )
}