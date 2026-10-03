export default function AnimatedBlock({children, show}: {show: Boolean}) {
    return (
        <div
            style={{
                display: "grid",
                gridTemplateRows: show ? "1fr" : "0fr",
                transition: "grid-template-rows 0.2s ease-in-out",
            }}
        >
            <div style={{overflow: "hidden"}}>
                {children}
            </div>
        </div>
    )
}