import type{CommandSnapshot,OperationalStatus,RescuerStatus}from'./types';
const supabaseUrl=(import.meta.env.VITE_SUPABASE_URL as string|undefined)?.replace(/\/$/,'');
const rescueApi=(import.meta.env.VITE_RESCUE_API_URL as string|undefined)||`${supabaseUrl||''}/functions/v1/rescue-tracker-api`;
const commandApi=(import.meta.env.VITE_COMMAND_API_URL as string|undefined)||`${supabaseUrl||''}/functions/v1/rescue-command-api`;
const publicKey=import.meta.env.VITE_SUPABASE_PUBLISHABLE_KEY as string|undefined;
const DEVICE_KEY='ns-rescue-device-token',COMMAND_KEY='ns-rescue-command-session';
export const configured=Boolean(supabaseUrl&&rescueApi&&commandApi);
async function json<T>(r:Response):Promise<T>{const t=await r.text();let d:any={};try{d=t?JSON.parse(t):{}}catch{throw new Error(t||`Request failed (${r.status})`)}if(!r.ok)throw new Error(d.error||`Request failed (${r.status})`);return d as T}
export function deviceToken(){return localStorage.getItem(DEVICE_KEY)||''}export function saveDeviceToken(v:string){localStorage.setItem(DEVICE_KEY,v)}export function clearDeviceToken(){localStorage.removeItem(DEVICE_KEY)}
export async function rescuer(body:Record<string,unknown>,token=deviceToken()){return json<any>(await fetch(rescueApi,{method:'POST',headers:{'content-type':'application/json',...(token?{'x-device-token':token}:{})},body:JSON.stringify(body)}))}
export async function loginRescuer(phone:string,pin:string){const d=await rescuer({action:'login',phone,pin,device_label:navigator.userAgent.slice(0,100)},'');if(d.device_token)saveDeviceToken(d.device_token);return d as RescuerStatus&{device_token:string}}
export async function registerRescuer(body:Record<string,unknown>){const d=await rescuer({action:'register',...body,device_label:navigator.userAgent.slice(0,100)},'');if(d.device_token)saveDeviceToken(d.device_token);return d}
export async function rescuerStatus(){return rescuer({action:'status'}) as Promise<RescuerStatus>}
export async function sendOperational(status:OperationalStatus,extra:Record<string,unknown>={}){const event=status==='on_mission'?'start':status==='off_duty'?'end':status;return rescuer({action:event,event,...extra})}
export async function sendSafe(payload:Record<string,unknown>){return rescuer({action:'safe',event:'safe',...payload})}
export async function sendSos(payload:Record<string,unknown>){return rescuer({action:'sos',event:'sos',...payload})}
export async function checkin(payload:Record<string,unknown>){return rescuer({action:'checkin',...payload})}
export async function markMessage(id:string,ack=false){return rescuer({action:ack?'ack_message':'read_message',message_id:id})}
export async function sendRescuerMessage(message:string,priority='normal'){return rescuer({action:'send_message',message,priority})}
export async function updateProfile(body:Record<string,unknown>){return rescuer({action:'profile',...body})}
export async function uploadPhoto(body:Record<string,unknown>){return rescuer({action:'mission_photo',...body})}
export interface CommandSession{access_token:string;refresh_token:string;expires_at:number;user?:{email?:string}}
export function commandSession():CommandSession|null{try{return JSON.parse(localStorage.getItem(COMMAND_KEY)||'null')}catch{return null}}
function saveCommand(s:CommandSession){localStorage.setItem(COMMAND_KEY,JSON.stringify(s));return s}export function clearCommand(){localStorage.removeItem(COMMAND_KEY)}
export async function commandLogin(email:string,password:string){if(!supabaseUrl||!publicKey)throw new Error('Command authentication is not configured');const d=await json<any>(await fetch(`${supabaseUrl}/auth/v1/token?grant_type=password`,{method:'POST',headers:{apikey:publicKey,'content-type':'application/json'},body:JSON.stringify({email,password})}));return saveCommand({access_token:d.access_token,refresh_token:d.refresh_token,expires_at:Date.now()+Number(d.expires_in||3600)*1000,user:d.user})}
async function validCommand(){let s=commandSession();if(!s)throw new Error('Coordinator login required');if(s.expires_at>Date.now()+60000)return s;if(!supabaseUrl||!publicKey)throw new Error('Command authentication is not configured');const d=await json<any>(await fetch(`${supabaseUrl}/auth/v1/token?grant_type=refresh_token`,{method:'POST',headers:{apikey:publicKey,'content-type':'application/json'},body:JSON.stringify({refresh_token:s.refresh_token})}));s=saveCommand({access_token:d.access_token,refresh_token:d.refresh_token||s.refresh_token,expires_at:Date.now()+Number(d.expires_in||3600)*1000,user:d.user});return s}
export async function command<T=any>(body:Record<string,unknown>):Promise<T>{const s=await validCommand();return json<T>(await fetch(commandApi,{method:'POST',headers:{'content-type':'application/json',authorization:`Bearer ${s.access_token}`},body:JSON.stringify(body)}))}
export async function snapshot(){return command<CommandSnapshot>({action:'snapshot'})}
