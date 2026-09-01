import { describe, expect, it, vi } from "vitest";
import type { QueueRecord } from "../domain/types";
import { synchronizeQueue } from "./syncService";

const record: QueueRecord = {
  id: "gps-1",
  kind: "GPS",
  capturedAt: "2026-08-31T01:02:03.000Z",
  syncState: "QUEUED",
  attempts: 0,
  payload: { captured_at: "2026-08-31T01:02:03.000Z" },
};

describe("queue synchronization", () => {
  it("removes successful uploads while preserving capture time", async () => {
    const save = vi.fn(async () => undefined);
    const remove = vi.fn(async () => undefined);
    const result = await synchronizeQueue([record], {
      upload: vi.fn(async (item) => {
        expect(item.capturedAt).toBe(record.capturedAt);
        expect(item.payload.captured_at).toBe(record.capturedAt);
      }),
      save,
      remove,
    });

    expect(result.remaining).toEqual([]);
    expect(result.synced[0].uploadedAt).toBeTruthy();
    expect(remove).toHaveBeenCalledWith(record.id);
  });

  it("persists a failed record and stops controlled retry", async () => {
    const save = vi.fn(async () => undefined);
    const result = await synchronizeQueue([record], {
      upload: vi.fn(async () => {
        throw new Error("offline");
      }),
      save,
      remove: vi.fn(async () => undefined),
    });

    expect(result.remaining[0]).toMatchObject({
      syncState: "FAILED",
      attempts: 1,
      lastError: "offline",
    });
    expect(save).toHaveBeenLastCalledWith(result.remaining[0]);
  });
});

