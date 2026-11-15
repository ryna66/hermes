import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { api } from './api'
import { ErrorDisplay } from './components'
import type { Support } from './types'

type PledgeType = 'FUNDING' | 'IN_KIND' | 'MENTORSHIP'
export default function PledgeForm({projectId, saved}: {projectId: string; saved: () => Promise<void>}) {
  const [type,setType]=useState<PledgeType>('FUNDING')
  const [amount,setAmount]=useState(''); const [currency,setCurrency]=useState(''); const [description,setDescription]=useState('')
  const [busy,setBusy]=useState(false); const [error,setError]=useState<unknown>(null); const [message,setMessage]=useState('')
  async function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault(); setError(null); setMessage('')
    if (!description.trim()) {setError(new Error('A support description is required.'));return}
    if (type==='FUNDING' && (!/^\d{1,17}(\.\d{1,2})?$/.test(amount) || !Number.isFinite(Number(amount)) || Number(amount)<0.01 || !/^[A-Z]{3}$/.test(currency.trim()))) {
      setError(new Error('Funding requires a positive amount with at most two decimal places and a three-letter uppercase currency.'));return
    }
    const input: Support={supportType:type,description:description.trim()}
    if (type==='FUNDING') {input.amount=Number(amount);input.currency=currency.trim()}
    setBusy(true)
    try {await api.pledge(projectId,input)} catch(e) {setError(e);setBusy(false);return}
    setMessage('Pledge recorded.');setAmount('');setCurrency('');setDescription('')
    window.dispatchEvent(new Event('notifications-changed'))
    try {await saved()} catch(e) {setError(e);setMessage('Pledge recorded, but the project could not be refreshed. Reload the page to see its latest status.')}
    setBusy(false)
  }
  return <section><h2>Pledge support</h2><ErrorDisplay error={error}/>{message && <p role="status">{message} <Link to="/industry">View your pledges</Link></p>}<form onSubmit={submit}><fieldset disabled={busy}><legend>Support details</legend>
    <label>Support type <select value={type} onChange={e => setType(e.target.value as PledgeType)}>{['FUNDING','IN_KIND','MENTORSHIP'].map(t => <option key={t}>{t}</option>)}</select></label>
    {type==='FUNDING' && <><label>Amount <input type="number" required min="0.01" step="0.01" value={amount} onChange={e => setAmount(e.target.value)}/></label><label>Currency <input required pattern="[A-Z]{3}" maxLength={3} value={currency} onChange={e => setCurrency(e.target.value)}/></label></>}
    <label>Support description <textarea required value={description} onChange={e => setDescription(e.target.value)}/></label><button type="submit">Submit pledge</button>
  </fieldset></form></section>
}
