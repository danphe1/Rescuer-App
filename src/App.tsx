import { useCallback, useEffect, useRef, useState } from "react";
import {
  Activity,
  AlertTriangle,
  BatteryMedium,
  CheckCircle2,
  ChevronRight,
  ClipboardCheck,
  Cloud,
  CloudOff,
  Home as HomeIcon,
  Map,
  MapPin,
  MessageCircle,
  Navigation,
  Radio,
  RefreshCw,
  Shield,
  ShieldAlert,
  UserRound,
  Wifi,
  WifiOff,
} from "lucide-react";
import { OperationalMap } from "./components/OperationalMap";
import { SosHoldButton } from "./components/SosHoldButton";
import {
  changeOperational,
  confirmSafe,
  raiseSos,
  safetyAt,
  trackingHealth,
} from "./domain/operations";
import type {
  AppState,
  OperationalState,
  QueueKind,
  QueueRecord,
} from "./domain/types";
import { mission } from "./data/sample";
import { getQueue, putQueue, removeQueue } from "./data/queueRepository";
import { upload } from "./data/api";
import { synchronizeQueue } from "./data/syncService";
type Screen =
  | "home"
  | "mission"
  | "map"
  | "checkin"
  | "messages"
  | "sos"
  | "activity"
  | "profile"
  | "tracking"
  | "sync";
const now = new Date();
const seed: AppState = {
  operational: "AVAILABLE",
  safety: "SAFE_CONFIRMED",
  sos: "NONE",
  safeConfirmedAt: new Date(now.getTime() - 2 * 60 * 60 * 1000).toISOString(),
  lastPosition: {
    latitude: 27.7172,
    longitude: 85.324,
    accuracy: 8,
    capturedAt: new Date(now.getTime() - 42_000).toISOString(),
    battery: 72,
  },
  trackingPermission: "GRANTED",
  locationEnabled: true,
  online: navigator.onLine,
  battery: 72,
  events: [],
  queue: [],
};
const labels: Record<string, string> = {
  AVAILABLE: "Available",
  ALERTED: "Alerted",
  EN_ROUTE: "En route",
  ON_MISSION: "On mission",
  RETURNING: "Returning",
  OFF_DUTY: "Off duty",
  SAFE_CONFIRMED: "SAFE confirmed",
  SAFE_DUE: "SAFE due",
  SAFE_OVERDUE: "SAFE overdue",
};
function freshRecord(
  kind: QueueKind,
  payload: Record<string, unknown>,
): QueueRecord {
  return {
    id: crypto.randomUUID(),
    kind,
    capturedAt: new Date().toISOString(),
    syncState: "QUEUED",
    attempts: 0,
    payload,
  };
}
export default function App() {
  const [screen, setScreen] = useState<Screen>("home");
  const [state, setState] = useState(seed);
  const [toast, setToast] = useState("");
  const syncing = useRef(false);
  const autoSyncKey = useRef("");
  useEffect(() => {
    getQueue()
      .then((q) => setState((s) => ({ ...s, queue: q })))
      .catch(() => setToast("Offline storage could not be opened."));
    const online = () => setState((s) => ({ ...s, online: navigator.onLine }));
    addEventListener("online", online);
    addEventListener("offline", online);
    return () => {
      removeEventListener("online", online);
      removeEventListener("offline", online);
    };
  }, []);
  useEffect(() => {
    const t = setInterval(
      () =>
        setState((s) =>
          s.safety === "SOS"
            ? s
            : { ...s, safety: safetyAt(s.safeConfirmedAt) },
        ),
      30000,
    );
    return () => clearInterval(t);
  }, []);
  const age = state.lastPosition
    ? Date.now() - new Date(state.lastPosition.capturedAt).getTime()
    : Infinity;
  const health = trackingHealth({
    ageMs: age,
    permission: state.trackingPermission === "GRANTED",
    locationEnabled: state.locationEnabled,
    online: state.online,
    locallyCapturing: !!state.lastPosition,
    queued: state.queue.filter((q) => q.kind === "GPS").length,
  });
  const notify = (m: string) => {
    setToast(m);
    setTimeout(() => setToast(""), 3500);
  };
  async function queue(
    kind: QueueKind,
    payload: Record<string, unknown>,
    message: string,
  ) {
    const r = freshRecord(kind, payload);
    await putQueue(r);
    setState((s) => ({ ...s, queue: [...s.queue, r] }));
    notify(message);
  }
  function transition(next: OperationalState) {
    setState((s) => changeOperational(s, next));
    notify(`Status changed to ${labels[next]}`);
  }
  async function safe() {
    setState((s) => confirmSafe(s));
    await queue(
      "EVENT",
      {
        event: "safe",
        captured_at: new Date().toISOString(),
        ...state.lastPosition,
      },
      state.online
        ? "SAFE recorded â€” syncing"
        : "SAFE recorded â€” queued offline",
    );
  }
  async function sos() {
    setState((s) => raiseSos(s));
    await queue(
      "SOS",
      {
        event: "sos",
        created_at: new Date().toISOString(),
        rescuer_id: "rescuer-001",
        rescuer_name: "Maya Gurung",
        team: "Bagmati Alpha",
        phone: "980-000-0142",
        operational_state: state.operational,
        gps: state.lastPosition,
        battery: state.battery,
        network: state.online ? "online" : "offline",
      },
      state.online
        ? "SOS raised â€” awaiting Command"
        : "SOS saved locally â€” retrying delivery",
    );
    setScreen("sos");
  }
  const sync = useCallback(async () => {
    if (!state.online) {
      notify("No network. Items remain safely queued.");
      return;
    }
    if (syncing.current || state.queue.length === 0) return;
    syncing.current = true;
    try {
      const result = await synchronizeQueue(state.queue, {
        upload,
        save: putQueue,
        remove: removeQueue,
      });
      setState((s) => ({ ...s, queue: result.remaining }));
      if (result.remaining.length > 0) {
        notify(result.remaining[0].lastError ?? "Sync failed");
      } else {
        notify("Offline queue synchronized");
      }
    } finally {
      syncing.current = false;
    }
  }, [state.online, state.queue]);
  useEffect(() => {
    const key = state.queue.map((record) => record.id).join(",");
    if (!state.online) {
      autoSyncKey.current = "";
    } else if (key && key !== autoSyncKey.current) {
      autoSyncKey.current = key;
      void sync();
    }
  }, [state.online, state.queue, sync]);
  const content = (() => {
    switch (screen) {
      case "mission":
        return <Mission state={state.operational} transition={transition} />;
      case "map":
        return <MapScreen />;
      case "checkin":
        return (
          <CheckIn
            onEvidence={async (category, file) => {
              const photoDataUrl = await new Promise<string>(
                (resolve, reject) => {
                  const reader = new FileReader();
                  reader.onload = () => resolve(String(reader.result));
                  reader.onerror = () => reject(reader.error);
                  reader.readAsDataURL(file);
                },
              );
              await queue(
                "MEDIA",
                {
                  action: "mission_photo",
                  category,
                  media_type: file.type.startsWith("video/")
                    ? "video"
                    : "photo",
                  photo_data_url: photoDataUrl,
                  mission_id: mission.id,
                  captured_at: new Date(
                    file.lastModified || Date.now(),
                  ).toISOString(),
                  ...state.lastPosition,
                },
                state.online
                  ? "Evidence saved â€” syncing"
                  : "Evidence saved â€” queued offline",
              );
            }}
            onSubmit={async (type, note) => {
              await queue(
                "CHECK_IN",
                {
                  action: "note",
                  type,
                  note,
                  mission_id: mission.id,
                  captured_at: new Date().toISOString(),
                  ...state.lastPosition,
                },
                state.online
                  ? "Check-in saved â€” syncing"
                  : "Check-in saved â€” queued offline",
              );
              setScreen("home");
            }}
          />
        );
      case "messages":
        return <Messages notify={notify} />;
      case "sos":
        return (
          <Sos
            state={state}
            acknowledge={() =>
              setState((s) => ({
                ...s,
                sos: "ACKNOWLEDGED",
                events: [
                  {
                    id: crypto.randomUUID(),
                    eventType: "sos_acknowledged",
                    actorId: "command",
                    missionId: mission.id,
                    timestamp: new Date().toISOString(),
                    metadata: {},
                  },
                  ...s.events,
                ],
              }))
            }
          />
        );
      case "activity":
        return <Timeline state={state} />;
      case "profile":
        return <Profile />;
      case "tracking":
        return (
          <Tracking
            state={state}
            health={health}
            stop={() => transition("OFF_DUTY")}
          />
        );
      case "sync":
        return <Sync state={state} sync={sync} />;
      default:
        return (
          <Home
            state={state}
            health={health}
            go={setScreen}
            transition={transition}
            safe={safe}
            sos={sos}
          />
        );
    }
  })();
  return (
    <div className="app">
      <header>
        <div className="brand">
          <div className="crest">âšœ</div>
          <div>
            <b>Nepal Scouts</b>
            <span>RESCUE â€¢ FIELD UNIT</span>
          </div>
        </div>
        <button
          className={`network ${state.online ? "online" : "offline"}`}
          onClick={() => setScreen("sync")}
        >
          {state.online ? <Wifi size={15} /> : <WifiOff size={15} />}{" "}
          {state.online ? "Online" : "Offline"}
          {state.queue.length > 0 && ` Â· ${state.queue.length} queued`}
        </button>
      </header>
      <main>{content}</main>
      <nav>
        <Nav
          icon={<HomeIcon />}
          label="Home"
          active={screen === "home"}
          onClick={() => setScreen("home")}
        />
        <Nav
          icon={<ClipboardCheck />}
          label="Mission"
          active={screen === "mission"}
          onClick={() => setScreen("mission")}
        />
        <Nav
          icon={<Map />}
          label="Map"
          active={screen === "map"}
          onClick={() => setScreen("map")}
        />
        <Nav
          icon={<Activity />}
          label="Activity"
          active={screen === "activity"}
          onClick={() => setScreen("activity")}
        />
        <Nav
          icon={<UserRound />}
          label="Profile"
          active={screen === "profile"}
          onClick={() => setScreen("profile")}
        />
      </nav>
      {toast && (
        <div className="toast" role="status">
          {toast}
        </div>
      )}
    </div>
  );
}
function Nav(p: {
  icon: React.ReactNode;
  label: string;
  active: boolean;
  onClick: () => void;
}) {
  return (
    <button className={p.active ? "active" : ""} onClick={p.onClick}>
      {p.icon}
      <span>{p.label}</span>
    </button>
  );
}
function Page({
  title,
  eyebrow,
  children,
}: {
  title: string;
  eyebrow?: string;
  children: React.ReactNode;
}) {
  return (
    <>
      <div className="page-title">
        {eyebrow && <span>{eyebrow}</span>}
        <h1>{title}</h1>
      </div>
      {children}
    </>
  );
}
function Home({
  state,
  health,
  go,
  transition,
  safe,
  sos,
}: {
  state: AppState;
  health: string;
  go: (s: Screen) => void;
  transition: (s: OperationalState) => void;
  safe: () => void;
  sos: () => void;
}) {
  return (
    <>
      <section className="identity">
        <div className="avatar">MG</div>
        <div>
          <span>FIELD RESCUER</span>
          <h1>Maya Gurung</h1>
          <p>Bagmati Alpha Â· Scout NS-0427</p>
        </div>
        <button aria-label="Open profile" onClick={() => go("profile")}>
          <ChevronRight />
        </button>
      </section>
      <section className="status-strip">
        <div>
          <small>OPERATIONAL</small>
          <b className="mission-blue">{labels[state.operational]}</b>
        </div>
        <div>
          <small>SAFETY</small>
          <b className={state.safety === "SAFE_CONFIRMED" ? "safe" : "danger"}>
            <Shield size={16} />
            {labels[state.safety] ?? "SOS ACTIVE"}
          </b>
        </div>
      </section>
      <section
        className={`tracking-banner ${health.toLowerCase()}`}
        onClick={() => go("tracking")}
      >
        <div className="pulse" />
        <div>
          <b>
            {health === "LOST" ? "TRACKING LOST" : health.replaceAll("_", " ")}
          </b>
          <span>
            {health === "OFFLINE_QUEUED"
              ? "GPS is capturing locally"
              : `Last GPS ${Math.round((Date.now() - new Date(state.lastPosition!.capturedAt).getTime()) / 1000)} sec ago Â· Â±${state.lastPosition?.accuracy}m`}
          </span>
        </div>
        <BatteryMedium />
        <b>{state.battery}%</b>
        <ChevronRight />
      </section>
      <section className="mission-summary" onClick={() => go("mission")}>
        <div>
          <span>CURRENT ASSIGNMENT Â· {mission.incidentId}</span>
          <h2>{mission.incidentName}</h2>
          <p>
            <MapPin /> {mission.area}
          </p>
        </div>
        <ChevronRight />
      </section>
      <h2 className="section-heading">Field actions</h2>
      <section className="action-grid">
        <button
          className="start-action"
          onClick={() =>
            state.operational === "AVAILABLE"
              ? transition("EN_ROUTE")
              : transition("ON_MISSION")
          }
        >
          <Navigation />
          <b>
            {state.operational === "AVAILABLE" ? "En Route" : "Start Mission"}
          </b>
          <span>
            {state.operational === "AVAILABLE"
              ? "Accept assignment"
              : "Begin active tracking"}
          </span>
        </button>
        <button onClick={() => go("map")}>
          <Map />
          <b>Map</b>
          <span>Area, route & team</span>
        </button>
        <button onClick={() => go("checkin")}>
          <ClipboardCheck />
          <b>Check In</b>
          <span>Send field update</span>
        </button>
        <button onClick={() => go("messages")}>
          <MessageCircle />
          <b>Message</b>
          <span>Command & team</span>
        </button>
      </section>
      <button className="safe-button" onClick={safe}>
        <CheckCircle2 />
        <span>
          <b>I AM SAFE</b>
          <small>Resets safety timer only</small>
        </span>
      </button>
      <SosHoldButton onConfirm={sos} />
      <div className="quick-links">
        <button onClick={() => go("sync")}>
          <Cloud />
          Offline Sync <b>{state.queue.length}</b>
        </button>
        <button onClick={() => go("tracking")}>
          <Shield />
          Tracking & Privacy
        </button>
      </div>
    </>
  );
}
function Mission({
  state,
  transition,
}: {
  state: OperationalState;
  transition: (s: OperationalState) => void;
}) {
  return (
    <Page title="Mission" eyebrow={mission.incidentId}>
      <section className="hero-card">
        <span>ASSIGNED INCIDENT</span>
        <h2>{mission.incidentName}</h2>
        <p>
          <MapPin /> {mission.area}
        </p>
      </section>
      <section className="detail-list">
        <Detail label="Operational status" value={labels[state]} />
        <Detail label="Team" value={mission.team} />
        <Detail label="Team leader" value={mission.leader} />
        <Detail label="Team members" value={mission.members.join(" Â· ")} />
        <Detail label="Task" value={mission.task} />
        <Detail label="Meeting point" value={mission.meetingPoint} />
        <Detail label="Safe route" value={mission.safeRoute} />
        <Detail label="Vehicle" value={mission.vehicle ?? "None assigned"} />
        <Detail
          label="Mission time"
          value={`Starts ${new Date(mission.startsAt).toLocaleString()}`}
        />
        <Detail
          label="Emergency contacts"
          value={mission.contacts.join(" Â· ")}
        />
      </section>
      <div className="stack-actions">
        {state === "AVAILABLE" && (
          <button onClick={() => transition("EN_ROUTE")}>Set En Route</button>
        )}
        {state === "EN_ROUTE" && (
          <button onClick={() => transition("ON_MISSION")}>
            Start Mission
          </button>
        )}
        {state === "ON_MISSION" && (
          <button className="amber" onClick={() => transition("RETURNING")}>
            Returning
          </button>
        )}
        {state === "RETURNING" && (
          <button className="outline" onClick={() => transition("OFF_DUTY")}>
            End Mission & Stop Tracking
          </button>
        )}
      </div>
    </Page>
  );
}
function Detail({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <span>{label}</span>
      <b>{value}</b>
    </div>
  );
}
function MapScreen() {
  return (
    <Page title="Operational Map" eyebrow="OSM Â· FIELD LAYERS">
      <OperationalMap />
      <div className="map-note">
        <Radio />
        <span>
          <b>Own route and assigned area shown</b>
          <small>
            Use the layers button to add hazards, shelters, medical, Command and
            affected locations.
          </small>
        </span>
      </div>
    </Page>
  );
}
function CheckIn({
  onSubmit,
  onEvidence,
}: {
  onSubmit: (type: string, note: string) => void;
  onEvidence: (category: string, file: File) => Promise<void>;
}) {
  const [type, setType] = useState("Reached location");
  const [note, setNote] = useState("");
  const [category, setCategory] = useState("incident");
  const [evidenceState, setEvidenceState] = useState("");
  const presets = [
    "Reached location",
    "Need medical",
    "Need transport",
    "Need food/water",
    "Area searched",
    "Victim found",
    "Custom note",
  ];
  return (
    <Page title="Quick Check-In" eyebrow="GPS + TIME ATTACHED">
      <div className="preset-grid">
        {presets.map((p) => (
          <button
            className={type === p ? "selected" : ""}
            onClick={() => setType(p)}
            key={p}
          >
            {p}
          </button>
        ))}
      </div>
      <label className="field">
        Field note
        <textarea
          value={note}
          onChange={(e) => setNote(e.target.value)}
          placeholder="Add details for Commandâ€¦"
        />
      </label>
      <div className="location-proof">
        <MapPin />
        <span>
          <b>27.71720, 85.32400</b>
          <small>Accuracy Â±8m Â· captured on submit</small>
        </span>
      </div>
      <button className="primary full" onClick={() => onSubmit(type, note)}>
        Submit Check-In
      </button>
      <p className="hint">
        Works offline. Original capture time and GPS are preserved.
      </p>
      <section className="evidence-capture">
        <span>FIELD EVIDENCE</span>
        <h2>Photo or video</h2>
        <p>Capture is saved locally first so upload never blocks field work.</p>
        <select value={category} onChange={(e) => setCategory(e.target.value)}>
          <option value="incident">Incident</option>
          <option value="victim_location">Victim location</option>
          <option value="damage">Damage</option>
          <option value="supplies">Supplies</option>
        </select>
        <label className="evidence-button">
          Capture / choose evidence
          <input
            type="file"
            accept="image/*,video/*"
            capture="environment"
            onChange={async (e) => {
              const file = e.target.files?.[0];
              if (!file) return;
              setEvidenceState("Saving evidence locallyâ€¦");
              try {
                await onEvidence(category, file);
                setEvidenceState("Evidence saved to offline queue.");
              } catch {
                setEvidenceState(
                  "Could not save evidence. Free device storage and retry.",
                );
              }
            }}
          />
        </label>
        {evidenceState && <b className="evidence-state">{evidenceState}</b>}
      </section>
    </Page>
  );
}
function Messages({ notify }: { notify: (s: string) => void }) {
  const [tab, setTab] = useState<"Command" | "Team">("Command");
  const [ack, setAck] = useState(false);
  return (
    <Page title="Messages" eyebrow="FIELD COMMUNICATIONS">
      <div className="segmented">
        <button
          className={tab === "Command" ? "active" : ""}
          onClick={() => setTab("Command")}
        >
          Command
        </button>
        <button
          className={tab === "Team" ? "active" : ""}
          onClick={() => setTab("Team")}
        >
          Team
        </button>
      </div>
      {tab === "Command" && !ack && (
        <article className="urgent">
          <span>
            <AlertTriangle /> URGENT Â· ACKNOWLEDGEMENT REQUIRED
          </span>
          <h3>River level rising near Sector C</h3>
          <p>
            Move to the north road and confirm when clear of the embankment.
          </p>
          <button
            onClick={() => {
              setAck(true);
              notify("Urgent message acknowledged");
            }}
          >
            Acknowledge
          </button>
        </article>
      )}
      <article className="message">
        <b>{tab === "Command" ? "Rescue Command" : "Sita Rai Â· Team Leader"}</b>
        <p>
          {tab === "Command"
            ? "Medical point moved to Shree Janata School."
            : "Meet at the eastern gate after area sweep."}
        </p>
        <small>Delivered Â· 09:42</small>
      </article>
      <label className="field">
        New message
        <textarea placeholder={`Message ${tab}â€¦`} />
      </label>
      <button
        className="primary full"
        onClick={() => notify("Message queued for delivery")}
      >
        Send to {tab}
      </button>
    </Page>
  );
}
function Sos({
  state,
  acknowledge,
}: {
  state: AppState;
  acknowledge: () => void;
}) {
  return (
    <Page title="SOS Emergency" eyebrow="SAFETY-CRITICAL">
      <section className={`sos-state ${state.sos.toLowerCase()}`}>
        <ShieldAlert />
        <h2>
          {state.sos === "ACKNOWLEDGED" ? "COMMAND ACKNOWLEDGED" : "SOS RAISED"}
        </h2>
        <p>
          {state.sos === "ACKNOWLEDGED"
            ? "Command has received your alert. Stay visible and follow instructions."
            : "Your SOS is stored and delivery will keep retrying until acknowledged."}
        </p>
        <b>
          {state.online
            ? "Delivery attempted Â· awaiting acknowledgement"
            : "Offline Â· SOS queued locally"}
        </b>
      </section>
      <section className="detail-list">
        <Detail
          label="Operational state (unchanged)"
          value={labels[state.operational]}
        />
        <Detail label="Position" value="27.71720, 85.32400 Â· Â±8m" />
        <Detail label="Battery" value={`${state.battery}%`} />
        <Detail label="Network" value={state.online ? "Online" : "Offline"} />
        <Detail
          label="Escalation"
          value="Team Leader escalation after 90 seconds"
        />
      </section>
      {state.sos === "RAISED" && (
        <button className="outline full" onClick={acknowledge}>
          Demo: receive Command acknowledgement
        </button>
      )}
      <p className="hint">
        SOS history remains in the immutable activity timeline after
        acknowledgement or resolution.
      </p>
    </Page>
  );
}
function Timeline({ state }: { state: AppState }) {
  const demo = [
    ...state.events,
    {
      id: "1",
      eventType: "alerted",
      actorId: "command",
      timestamp: new Date(Date.now() - 7200000).toISOString(),
      metadata: { incidentId: mission.incidentId },
    },
    {
      id: "2",
      eventType: "assignment_received",
      actorId: "command",
      timestamp: new Date(Date.now() - 7500000).toISOString(),
      metadata: { team: mission.team },
    },
  ];
  return (
    <Page title="Activity Timeline" eyebrow="IMMUTABLE FIELD LOG">
      <div className="timeline">
        {demo.map((e) => (
          <article key={e.id}>
            <div className="timeline-dot" />
            <small>
              {new Date(e.timestamp).toLocaleTimeString([], {
                hour: "2-digit",
                minute: "2-digit",
              })}
            </small>
            <h3>{e.eventType.replaceAll("_", " ")}</h3>
            <p>
              {e.actorId === "command" ? "Command Center" : "Maya Gurung"} Â·{" "}
              {Object.keys(e.metadata).length
                ? JSON.stringify(e.metadata)
                : "Recorded with field context"}
            </p>
          </article>
        ))}
      </div>
    </Page>
  );
}
function Profile() {
  return (
    <Page title="Rescuer Profile" eyebrow="APPROVED DEVICE">
      <section className="profile-hero">
        <div className="avatar large">MG</div>
        <h2>Maya Gurung</h2>
        <p>Scout ID NS-0427 Â· Bagmati Alpha</p>
        <span>
          <CheckCircle2 /> Device approved
        </span>
      </section>
      <section className="detail-list">
        <Detail label="Phone" value="980-000-0142" />
        <Detail label="Blood group" value="O+" />
        <Detail label="Skills" value="First aid Â· Swift water Â· Radio" />
        <Detail label="Emergency contact" value="Tara Gurung Â· 980-000-0143" />
        <Detail label="District deployed" value="Kathmandu" />
        <Detail label="Team" value="Bagmati Alpha" />
      </section>
      <div className="notice">
        <Shield />
        Profile changes never bypass approved-device enforcement.
      </div>
    </Page>
  );
}
function Tracking({
  state,
  health,
  stop,
}: {
  state: AppState;
  health: string;
  stop: () => void;
}) {
  return (
    <Page title="Tracking & Privacy" eyebrow="MISSION-BOUND LOCATION">
      <section className={`tracking-hero ${health.toLowerCase()}`}>
        <div className="pulse" />
        <div>
          <span>TRACKING HEALTH</span>
          <h2>{health.replaceAll("_", " ")}</h2>
          <p>Active for {mission.incidentId}</p>
        </div>
      </section>
      <section className="detail-list">
        <Detail
          label="Tracking"
          value={
            state.operational === "ON_MISSION" ||
            state.operational === "RETURNING"
              ? "ON â€” active mission"
              : "OFF â€” mission not active"
          }
        />
        <Detail label="GPS permission" value={state.trackingPermission} />
        <Detail
          label="Current interval"
          value={
            state.operational === "ON_MISSION"
              ? "15 seconds Â· active rescue"
              : "2 minutes Â· standby"
          }
        />
        <Detail label="Last update" value="42 seconds ago Â· Â±8m" />
        <Detail label="Battery mode" value="Standard Â· 72%" />
        <Detail
          label="Offline GPS queue"
          value={`${state.queue.filter((q) => q.kind === "GPS").length} points`}
        />
      </section>
      <div className="notice">
        <Shield />
        GPS runs only for an approved active mission. Browser suspension may
        interrupt capture.
      </div>
      {state.operational === "RETURNING" && (
        <button className="outline full" onClick={stop}>
          End Mission & Stop Tracking
        </button>
      )}
    </Page>
  );
}
function Sync({ state, sync }: { state: AppState; sync: () => void }) {
  const count = (k: QueueKind) =>
    state.queue.filter((q) => q.kind === k).length;
  return (
    <Page
      title="Offline Sync"
      eyebrow={state.online ? "SERVER AVAILABLE" : "NO NETWORK"}
    >
      <section className={`connectivity ${state.online ? "ok" : "warn"}`}>
        {state.online ? <Cloud /> : <CloudOff />}
        <div>
          <h2>{state.online ? "Online" : "Offline â€” capture continues"}</h2>
          <p>
            {state.online
              ? "Queued records can synchronize."
              : "Supported records remain on this device with original timestamps."}
          </p>
        </div>
      </section>
      <div className="queue-counts">
        <Detail label="GPS points" value={String(count("GPS"))} />
        <Detail label="Photos / videos" value={String(count("MEDIA"))} />
        <Detail label="Check-ins" value={String(count("CHECK_IN"))} />
        <Detail
          label="SOS / events"
          value={String(count("SOS") + count("EVENT"))}
        />
      </div>
      {state.queue.some((q) => q.syncState === "FAILED") && (
        <div className="error">
          <AlertTriangle />
          Server integration is not configured. Records remain durable and
          retryable.
        </div>
      )}
      <button
        className="primary full"
        disabled={!state.online || !state.queue.length}
        onClick={sync}
      >
        <RefreshCw />
        Sync queued records
      </button>
      <p className="hint">Capture time is never replaced by upload time.</p>
    </Page>
  );
}

