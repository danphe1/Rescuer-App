import { createClient } from 'npm:@supabase/supabase-js@2.102.0'

const cors={
  'access-control-allow-origin':'*',
  'access-control-allow-headers':'content-type, x-device-token, authorization, apikey',
  'access-control-allow-methods':'POST,OPTIONS',
  'content-type':'application/json'
}
const enc=new TextEncoder()
async function sha(s:string){const d=await crypto.subtle.digest('SHA-256',enc.encode(s));return Array.from(new Uint8Array(d)).map(b=>b.toString(16).padStart(2,'0')).join('')}
function rawToken(){const a=new Uint8Array(32);crypto.getRandomValues(a);return Array.from(a).map(b=>b.toString(16).padStart(2,'0')).join('')}
function out(body:any,status=200){return new Response(JSON.stringify(body),{status,headers:cors})}
function cleanPhoto(v:any){const s=String(v||'');return s.startsWith('data:image/')&&s.length<800000?s:null}

Deno.serve(async(req:Request)=>{
  if(req.method==='OPTIONS')return new Response('ok',{headers:cors})
  if(req.method!=='POST')return out({ok:false,error:'method'},405)
  const admin=createClient(Deno.env.get('SUPABASE_URL')!,Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!,{auth:{persistSession:false,autoRefreshToken:false}})
  const activity=async(device_id:string,event_type:string,message:any=null,loc:any=null,metadata:any={},mission_id:string|null=null)=>{
    await admin.from('rescue_activity_events').insert({device_id,mission_id,event_type,message:message?String(message).slice(0,3000):null,latitude:loc?.latitude??null,longitude:loc?.longitude??null,accuracy:loc?.accuracy??null,metadata})
  }
  try{
    const b=await req.json()
    const action=String(b.action||'')

    if(action==='register'){
      const code=String(b.team_code||'').trim(),name=String(b.name||'').trim(),phone=String(b.phone||'').trim(),pin=String(b.pin||'').trim(),address=String(b.address||'').trim()||null,blood=String(b.blood_group||'').trim()||null,ecName=String(b.emergency_contact_name||'').trim()||null,ecPhone=String(b.emergency_contact_phone||'').trim()||null,photo=cleanPhoto(b.profile_photo_data_url),label=String(b.device_label||'').slice(0,120)||null
      if(!code||name.length<2||!phone||pin.length<4)return out({ok:false,error:'team code, name, phone and a 4+ digit rescuer code are required'},400)
      const ch=await sha(code)
      const {data:team}=await admin.from('rescue_teams').select('id,name,checkin_minutes,active').eq('join_code_hash',ch).eq('active',true).maybeSingle()
      if(!team)return out({ok:false,error:'invalid team code'},403)
      const {data:existing}=await admin.from('rescue_devices').select('id').eq('phone',phone).eq('active',true).limit(1)
      if(existing&&existing.length)return out({ok:false,error:'This phone number is already registered. Use Rescuer Login.'},409)
      const raw=rawToken()
      const {data:dev,error}=await admin.from('rescue_devices').insert({team_id:team.id,rescuer_name:name,phone,address,blood_group:blood,emergency_contact_name:ecName,emergency_contact_phone:ecPhone,profile_photo_data_url:photo,profile_updated_at:new Date().toISOString(),rescuer_pin_hash:await sha(pin),token_hash:await sha(raw),mission_status:'pending',approval_status:'pending',device_label:label}).select('id').single()
      if(error)throw error
      await admin.from('rescue_device_audit').insert({device_id:dev.id,action:'registered',note:'New rescuer registration awaiting coordinator approval'})
      await activity(dev.id,'registered','New rescuer registration awaiting coordinator approval',null,{team_id:team.id},null)
      return out({ok:true,pending:true,approval_status:'pending',device_id:dev.id,device_token:raw,team})
    }

    if(action==='login'){
      const phone=String(b.phone||'').trim(),pin=String(b.pin||'').trim()
      if(!phone||!pin)return out({ok:false,error:'phone number and rescuer code are required'},400)
      const ph=await sha(pin)
      const {data:dev}=await admin.from('rescue_devices').select('id,team_id,rescuer_name,phone,rescuer_pin_hash,active,approval_status,mission_status,last_lat,last_long,last_accuracy,active_mission_id').eq('phone',phone).eq('active',true).order('joined_at',{ascending:false}).limit(1).maybeSingle()
      if(!dev||dev.rescuer_pin_hash!==ph)return out({ok:false,error:'Phone number or rescuer code is incorrect'},403)
      const raw=rawToken()
      await admin.from('rescue_devices').update({token_hash:await sha(raw),device_label:String(b.device_label||'Returning device').slice(0,120),last_seen_at:new Date().toISOString()}).eq('id',dev.id)
      const {data:team}=await admin.from('rescue_teams').select('id,name,checkin_minutes').eq('id',dev.team_id).single()
      await admin.from('rescue_device_audit').insert({device_id:dev.id,action:'return_login',note:'Rescuer logged in with phone and rescuer code'})
      await activity(dev.id,'login','Rescuer logged in',dev.last_lat==null?null:{latitude:dev.last_lat,longitude:dev.last_long,accuracy:dev.last_accuracy},{},dev.active_mission_id)
      return out({ok:true,pending:dev.approval_status!=='approved',approval_status:dev.approval_status,mission_status:dev.mission_status,active_mission_id:dev.active_mission_id,device_id:dev.id,device_token:raw,team,rescuer_name:dev.rescuer_name})
    }

    const raw=req.headers.get('x-device-token')||String(b.device_token||'')
    if(!raw)return out({ok:false,error:'device token required'},401)
    const {data:dev,error:ve}=await admin.from('rescue_devices').select('*').eq('token_hash',await sha(raw)).maybeSingle()
    if(ve)throw ve
    if(!dev||!dev.active)return out({ok:false,error:'invalid device'},401)

    if(action==='status'){
      const {data:msgs}=await admin.from('rescue_messages').select('id,message,priority,created_at,delivered_at,read_at').eq('device_id',dev.id).is('cancelled_at',null).is('read_at',null).order('created_at',{ascending:true}).limit(20)
      if(msgs?.length)await admin.from('rescue_messages').update({delivered_at:new Date().toISOString()}).in('id',msgs.map((m:any)=>m.id)).is('delivered_at',null)
      return out({ok:true,approval_status:dev.approval_status,mission_status:dev.mission_status,active_mission_id:dev.active_mission_id,team_id:dev.team_id,messages:msgs||[]})
    }

    if(action==='read_message'){
      const id=String(b.message_id||'')
      await admin.from('rescue_messages').update({read_at:new Date().toISOString(),acknowledged_at:new Date().toISOString()}).eq('id',id).eq('device_id',dev.id)
      await activity(dev.id,'message_acknowledged','Coordinator message acknowledged',dev.last_lat==null?null:{latitude:dev.last_lat,longitude:dev.last_long,accuracy:dev.last_accuracy},{message_id:id},dev.active_mission_id)
      return out({ok:true})
    }

    if(dev.approval_status!=='approved')return out({ok:false,error:dev.approval_status==='revoked'?'device revoked':dev.approval_status==='hold'?'account on hold':'awaiting coordinator approval',approval_status:dev.approval_status},403)

    if(action==='profile'){
      const patch:any={profile_updated_at:new Date().toISOString()}
      for(const [src,dst] of [['name','rescuer_name'],['phone','phone'],['address','address'],['blood_group','blood_group'],['emergency_contact_name','emergency_contact_name'],['emergency_contact_phone','emergency_contact_phone']])if(b[src]!==undefined&&String(b[src]).trim()!=='')patch[dst]=String(b[src]).trim()
      const p=cleanPhoto(b.profile_photo_data_url);if(p)patch.profile_photo_data_url=p
      if(String(b.new_pin||'').length>=4)patch.rescuer_pin_hash=await sha(String(b.new_pin))
      const {error}=await admin.from('rescue_devices').update(patch).eq('id',dev.id);if(error)throw error
      await activity(dev.id,'profile_updated','Rescuer profile updated',dev.last_lat==null?null:{latitude:dev.last_lat,longitude:dev.last_long,accuracy:dev.last_accuracy},{},dev.active_mission_id)
      return out({ok:true})
    }

    if(action==='mission_photo'){
      const p=cleanPhoto(b.photo_data_url);if(!p)return out({ok:false,error:'valid photo required'},400)
      const row={device_id:dev.id,mission_id:dev.active_mission_id||null,photo_data_url:p,caption:String(b.caption||'').slice(0,500)||null,latitude:b.latitude==null?null:Number(b.latitude),longitude:b.longitude==null?null:Number(b.longitude),accuracy:b.accuracy==null?null:Number(b.accuracy),captured_at:new Date(b.captured_at||Date.now()).toISOString()}
      const {error}=await admin.from('rescue_mission_photos').insert(row);if(error)throw error
      await activity(dev.id,'mission_photo',row.caption||'Mission photo uploaded',row,{},dev.active_mission_id)
      return out({ok:true})
    }

    if(action==='send_message'){
      const msg=String(b.message||'').trim();if(!msg)return out({ok:false,error:'message required'},400)
      await admin.from('rescue_messages').insert({device_id:dev.id,team_id:dev.team_id,sender_type:'rescuer',message:msg,priority:String(b.priority||'normal')})
      await activity(dev.id,'message_to_command',msg,dev.last_lat==null?null:{latitude:dev.last_lat,longitude:dev.last_long,accuracy:dev.last_accuracy},{},dev.active_mission_id)
      return out({ok:true})
    }

    const points=Array.isArray(b.points)?b.points:[],event=String(b.event||'location'),note=String(b.note||'').slice(0,3000)||null
    const now=new Date().toISOString()
    let missionId:string|null=dev.active_mission_id||null

    if(event==='start' && !missionId){
      const {data:m,error:me}=await admin.from('rescue_missions').insert({device_id:dev.id,started_at:now,status:'active'}).select('id').single()
      if(me)throw me
      missionId=m.id
    }

    let last:any=null
    if(points.length){
      const nowMs=Date.now()
      const rows=points.slice(-500).map((p:any)=>{
        const recorded=new Date(p.recorded_at||Date.now())
        return {
          device_id:dev.id,
          mission_id:missionId,
          recorded_at:recorded.toISOString(),
          latitude:Number(p.latitude),longitude:Number(p.longitude),
          accuracy:p.accuracy==null?null:Number(p.accuracy),
          battery:p.battery==null?null:Math.max(0,Math.min(100,Number(p.battery))),
          event_type:['location','safe','sos','note','mission_start','mission_end','returning'].includes(String(p.event_type))?String(p.event_type):'location',
          note:String(p.note||'').slice(0,500)||null,
          uploaded_from_offline:Boolean(p.offline)||nowMs-recorded.getTime()>120000
        }
      }).filter((r:any)=>Number.isFinite(r.latitude)&&Number.isFinite(r.longitude)&&r.latitude>=-90&&r.latitude<=90&&r.longitude>=-180&&r.longitude<=180)
      if(rows.length){const {error}=await admin.from('rescue_locations').insert(rows);if(error)throw error;last=rows[rows.length-1]}
    }

    const patch:any={last_seen_at:now}
    if(note)patch.last_note=note
    if(last){patch.last_lat=last.latitude;patch.last_long=last.longitude;patch.last_accuracy=last.accuracy;patch.battery=last.battery}

    if(event==='start'){
      patch.active_mission_id=missionId;patch.mission_status='active';patch.mission_started_at=now;patch.mission_ended_at=null;patch.sos=false
      if(missionId)await admin.from('rescue_missions').update({status:'active',updated_at:now}).eq('id',missionId)
    }else if(event==='safe'){
      patch.mission_status='safe';patch.last_safe_at=now;patch.sos=false
      if(missionId)await admin.from('rescue_missions').update({status:'safe',updated_at:now}).eq('id',missionId)
    }else if(event==='sos'){
      patch.mission_status='sos';patch.sos=true
      if(missionId)await admin.from('rescue_missions').update({status:'sos',updated_at:now}).eq('id',missionId)
      await admin.from('rescue_device_audit').insert({device_id:dev.id,action:'sos',note:note||'SOS from rescuer'})
    }else if(event==='returning'){
      patch.mission_status='returning';patch.sos=false
      if(missionId)await admin.from('rescue_missions').update({status:'returning',updated_at:now}).eq('id',missionId)
    }else if(event==='end'){
      patch.mission_status='ended';patch.mission_ended_at=now;patch.sos=false;patch.active_mission_id=null
      if(missionId)await admin.from('rescue_missions').update({status:'ended',ended_at:now,updated_at:now}).eq('id',missionId)
    }else if(event==='report'){
      if(!note)return out({ok:false,error:'mission report required'},400)
      patch.mission_report=note;patch.mission_reported_at=now
      await admin.from('rescue_mission_reports').insert({device_id:dev.id,mission_id:missionId,report_text:note,latitude:last?.latitude??dev.last_lat,longitude:last?.longitude??dev.last_long,accuracy:last?.accuracy??dev.last_accuracy,mission_started_at:dev.mission_started_at,mission_ended_at:dev.mission_ended_at})
    }

    const {error:ue}=await admin.from('rescue_devices').update(patch).eq('id',dev.id);if(ue)throw ue
    const loc=last||(dev.last_lat==null?null:{latitude:dev.last_lat,longitude:dev.last_long,accuracy:dev.last_accuracy})
    if(event!=='location')await activity(dev.id,event==='start'?'mission_start':event==='end'?'mission_end':event,note||({start:'Mission started',safe:'Rescuer marked safe',sos:'SOS activated',returning:'Rescuer returning',end:'Mission ended',note:'Mission update',report:'Mission report submitted'} as any)[event]||event,loc,{mission_status:patch.mission_status||dev.mission_status},missionId)
    return out({ok:true,event,received:points.length,mission_status:patch.mission_status||dev.mission_status,active_mission_id:event==='end'?null:missionId,server_time:now})
  }catch(e){return out({ok:false,error:e instanceof Error?e.message:'failed'},500)}
})
