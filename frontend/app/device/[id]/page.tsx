'use client'

import React, {use} from "react";

export default function Page({
    params,
}: {
    params: Promise<{ id: string }>;
}) {
    const { id } = use(params);

    return (
        <div>
            <p>Showing device {id}</p>
        </div>
    )
}
