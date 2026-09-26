'use client'

import React, {use} from "react";
import DefaultPage from "@/components/DefaultPage";

export default function Page({params}: {
    params: Promise<{ id: string }>;
}) {
    const {id} = use(params);

    return (
        <DefaultPage>
            <p
                style={{
                    fontSize: "4em"
                }}
            >
                Showing device {id}
            </p>
        </DefaultPage>
    )
}
