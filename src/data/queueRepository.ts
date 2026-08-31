import type{QueueRecord}from'../domain/types';
const DB='nepal-scouts-rescuer',STORE='offline-queue';
function db():Promise<IDBDatabase>{return new Promise((resolve,reject)=>{const r=indexedDB.open(DB,1);r.onupgradeneeded=()=>r.result.createObjectStore(STORE,{keyPath:'id'});r.onsuccess=()=>resolve(r.result);r.onerror=()=>reject(r.error)})}
export async function putQueue(record:QueueRecord){const d=await db();return new Promise<void>((resolve,reject)=>{const tx=d.transaction(STORE,'readwrite');tx.objectStore(STORE).put(record);tx.oncomplete=()=>resolve();tx.onerror=()=>reject(tx.error)})}
export async function getQueue():Promise<QueueRecord[]>{const d=await db();return new Promise((resolve,reject)=>{const r=d.transaction(STORE).objectStore(STORE).getAll();r.onsuccess=()=>resolve(r.result);r.onerror=()=>reject(r.error)})}
export async function removeQueue(id:string){const d=await db();return new Promise<void>((resolve,reject)=>{const tx=d.transaction(STORE,'readwrite');tx.objectStore(STORE).delete(id);tx.oncomplete=()=>resolve();tx.onerror=()=>reject(tx.error)})}
