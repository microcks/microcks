/** The status of a step attempt: Microcks records none (0 here) when the call got no response. */
export const statusLabel = (code: number | undefined): string => (code ? String(code) : 'no response');

/** Whether a recorded status is a success; an attempt without response is not. */
export const isHttpOk = (code: number): boolean => code > 0 && code < 300;
