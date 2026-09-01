import type { QueueRecord } from "../domain/types";

export interface SyncDependencies {
  upload(record: QueueRecord): Promise<unknown>;
  save(record: QueueRecord): Promise<void>;
  remove(id: string): Promise<void>;
}

export interface SyncResult {
  remaining: QueueRecord[];
  synced: QueueRecord[];
}

export async function synchronizeQueue(
  records: QueueRecord[],
  dependencies: SyncDependencies,
): Promise<SyncResult> {
  const remaining: QueueRecord[] = [];
  const synced: QueueRecord[] = [];

  for (const record of records) {
    const syncing = { ...record, syncState: "SYNCING" as const };
    await dependencies.save(syncing);

    try {
      await dependencies.upload(syncing);
      const completed = {
        ...syncing,
        syncState: "SYNCED" as const,
        uploadedAt: new Date().toISOString(),
      };
      synced.push(completed);
      await dependencies.remove(record.id);
    } catch (error) {
      const failed = {
        ...syncing,
        syncState: "FAILED" as const,
        attempts: record.attempts + 1,
        lastError: error instanceof Error ? error.message : "Upload failed",
      };
      await dependencies.save(failed);
      remaining.push(failed);
      remaining.push(...records.slice(records.indexOf(record) + 1));
      break;
    }
  }

  return { remaining, synced };
}

