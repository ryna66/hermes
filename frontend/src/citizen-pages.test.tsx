import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter } from 'react-router-dom'
import { api, ApiError } from './api'
import SubmitChallenge from './SubmitChallenge'
import { validateChallenge } from './challenge-validation'
import type { ChallengeFields } from './challenge-validation'
import { MyChallenges, Notifications } from './citizen-pages'
import type { Challenge, Notification, OwnChallenge, Page } from './types'
import LocationPicker from './LocationPicker'
const map = vi.hoisted(() => ({click: undefined as undefined | ((e: unknown) => void), tileError: undefined as undefined | (() => void), pan: vi.fn(), remove: vi.fn()}))
vi.mock('leaflet', () => ({default: {
  map: () => ({setView(){return this}, on: (_: string, handler: (e: unknown) => void) => {map.click = handler}, panTo: map.pan, remove: map.remove}),
  tileLayer: () => ({addTo(){return this}, on: (_: string, handler: () => void) => {map.tileError = handler}, off: vi.fn()}),
  circleMarker: () => ({addTo(){return this}, setLatLng: vi.fn(), remove: vi.fn()}),
}}))
afterEach(() => {vi.restoreAllMocks(); vi.unstubAllGlobals()})
const valid: ChallengeFields = {title:'Broken borewell',description:'No clean water',district:'Gumla',affectedPeople:'12',latitude:'23',longitude:'84',category:''}
function page<T>(content: T[], number=0, last=true): Page<T> {return {content,number,last,size:20,totalElements:content.length,totalPages: last ? 1 : 2}}
function showSubmit() {render(<MemoryRouter><SubmitChallenge/></MemoryRouter>)}
function fill() {for (const [label,key] of [['Title','title'],['Description','description'],['District','district'],['Affected people','affectedPeople'],['Latitude','latitude'],['Longitude','longitude']] as const) fireEvent.change(screen.getByLabelText(label), {target:{value:valid[key]}})}
describe('citizen submission', () => {
  it('mirrors backend required fields, lengths, coordinates, integer and media rules', () => {
    expect(validateChallenge(valid)).toEqual([])
    expect(validateChallenge({...valid,title:' ',description:' ',district:' ',latitude:'',longitude:'',affectedPeople:''})).toHaveLength(6)
    expect(validateChallenge({...valid,title:'x'.repeat(256),district:'x'.repeat(256),latitude:'91',longitude:'-181',affectedPeople:'1.5',category:'invalid'}, new File(['x'],'x',{type:'text/plain'}),new File(['x'],'x',{type:'text/plain'}))).toHaveLength(8)
    expect(validateChallenge({...valid,latitude:'-90',longitude:'180',affectedPeople:'2147483647'})).toEqual([])
    expect(validateChallenge(valid, new File([new Uint8Array(1048577)],'p.png',{type:'image/png'}))).toHaveLength(1)
    for (const value of ['0','-1','2147483648','NaN']) expect(validateChallenge({...valid,affectedPeople:value})).toHaveLength(1)
  })
  it('shows validation and prevents an invalid request', () => {
    const submit = vi.spyOn(api,'submitChallenge'); showSubmit(); fireEvent.click(screen.getByRole('button',{name:'Submit challenge'}))
    expect(screen.getByRole('alert').textContent).toContain('title is required.'); expect(submit).not.toHaveBeenCalled()
  })
  it('submits fields and optional files and shows aiStatus with a detail link', async () => {
    const submit = vi.spyOn(api,'submitChallenge').mockResolvedValue({id:'c1',aiStatus:'MANUAL_REVIEW'} as Challenge)
    showSubmit(); fill()
    const photo = new File(['photo'],'photo.png',{type:'image/png'}), audio = new File(['audio'],'note.wav',{type:'audio/wav'})
    fireEvent.change(screen.getByLabelText('Photo (optional)'),{target:{files:[photo]}}); fireEvent.change(screen.getByLabelText('Upload audio'),{target:{files:[audio]}})
    fireEvent.click(screen.getByRole('button',{name:'Submit challenge'}))
    expect(await screen.findByRole('status')).toHaveProperty('textContent','AI suggestion status: MANUAL_REVIEW')
    expect(screen.getByRole('link',{name:'View your challenge'}).getAttribute('href')).toBe('/challenges/c1')
    expect(submit).toHaveBeenCalledWith({...valid,latitude:23,longitude:84,affectedPeople:12,category:null},photo,audio)
  })
  it('uses the API multipart contract for photo and audio attachments', async () => {
    const fetcher = vi.fn().mockResolvedValue(new Response('{}')); vi.stubGlobal('fetch', fetcher)
    const photo = new File(['photo'],'p.png',{type:'image/png'}), audio = new File(['audio'],'a.wav',{type:'audio/wav'})
    await api.submitChallenge({title:'Problem',description:'Details',latitude:23,longitude:84,district:'Gumla',affectedPeople:2}, photo, audio)
    const [url, options] = fetcher.mock.calls[0]
    expect(url).toBe('http://localhost:8080/api/challenges'); expect(options.headers.has('Content-Type')).toBe(false)
    expect(options.body.get('challenge').type).toBe('application/json')
    expect(options.body.get('photo').name).toBe('p.png'); expect(options.body.get('audio').name).toBe('a.wav')
  })
  it('keeps typed description and permits submission on MANUAL_ENTRY', async () => {
    vi.spyOn(api,'transcribe').mockResolvedValue({text:null,status:'MANUAL_ENTRY',message:'Transcription unavailable; type manually.'})
    const submit = vi.spyOn(api,'submitChallenge').mockResolvedValue({id:'c1',aiStatus:'ENRICHED'} as Challenge)
    showSubmit(); fill(); fireEvent.change(screen.getByLabelText('Upload audio'),{target:{files:[new File(['abc'],'a.wav',{type:'audio/wav'})]}})
    fireEvent.click(screen.getByText('Transcribe audio')); expect(await screen.findByText('Transcription unavailable; type manually.')).toBeTruthy()
    expect(screen.getByLabelText('Description')).toHaveProperty('value',valid.description)
    expect(api.transcribe).toHaveBeenCalledWith({audioBase64:'YWJj',mimeType:'audio/wav',language:'en'})
    fireEvent.click(screen.getByRole('button',{name:'Submit challenge'})); await waitFor(() => expect(submit).toHaveBeenCalled())
  })
  it('fills an editable description when transcription succeeds', async () => {
    vi.spyOn(api,'transcribe').mockResolvedValue({text:'Spoken problem',status:'TRANSCRIBED',message:'Transcription ready'})
    showSubmit(); fireEvent.change(screen.getByLabelText('Upload audio'),{target:{files:[new File(['abc'],'a.wav',{type:'audio/wav'})]}}); fireEvent.click(screen.getByText('Transcribe audio'))
    await waitFor(() => expect(screen.getByLabelText('Description')).toHaveProperty('value','Spoken problem'))
    fireEvent.change(screen.getByLabelText('Description'),{target:{value:'Edited'}}); expect(screen.getByLabelText('Description')).toHaveProperty('value','Edited')
  })
  it('checks the separate transcription size limit before calling the API', async () => {
    const transcribe = vi.spyOn(api,'transcribe'); showSubmit(); fireEvent.change(screen.getByLabelText('Upload audio'),{target:{files:[new File([new Uint8Array(1048577)],'a.wav',{type:'audio/wav'})]}}); fireEvent.click(screen.getByText('Transcribe audio'))
    expect(await screen.findByRole('alert')).toHaveProperty('textContent',expect.stringContaining('1048576')); expect(transcribe).not.toHaveBeenCalled()
  })
  it('uses geolocation and shows permission errors without losing input', async () => {
    const getCurrentPosition = vi.fn().mockImplementationOnce(success => success({coords:{latitude:24,longitude:85}})).mockImplementationOnce((_success, failure) => failure({message:'Permission denied'}))
    vi.stubGlobal('navigator',{geolocation:{getCurrentPosition}}); showSubmit(); fireEvent.click(screen.getByText('Use my location')); expect(screen.getByLabelText('Latitude')).toHaveProperty('value','24'); expect(screen.getByLabelText('Longitude')).toHaveProperty('value','85')
    fireEvent.click(screen.getByText('Use my location')); expect(await screen.findByRole('alert')).toHaveProperty('textContent',expect.stringContaining('Permission denied'))
  })
  it('records audio and stops microphone tracks', async () => {
    const stop = vi.fn()
    class Recorder {
      state = 'inactive'; mimeType = 'audio/webm;codecs=opus'; ondataavailable?: (e: {data:Blob}) => void; onstop?: () => void
      start() {this.state = 'recording'}
      stop() {this.state = 'inactive'; this.ondataavailable?.({data:new Blob(['abc'])}); this.onstop?.()}
    }
    vi.stubGlobal('MediaRecorder',Recorder); vi.stubGlobal('navigator',{mediaDevices:{getUserMedia:vi.fn().mockResolvedValue({getTracks:()=>[{stop}]})}})
    showSubmit(); fireEvent.click(screen.getByText('Record audio')); fireEvent.click(await screen.findByText('Stop recording'))
    expect(await screen.findByText(/Selected audio: voice-note/)).toBeTruthy(); expect(stop).toHaveBeenCalled()
  })
  it('uses a clicked map coordinate and retains coordinate entry on tile failure', () => {
    const change = vi.fn(); const view = render(<LocationPicker latitude="" longitude="" onChange={change}/>)
    act(() => map.click?.({latlng:{lat:24,wrap:()=>({lng:86})}})); expect(change).toHaveBeenCalledWith('24','86')
    act(() => map.tileError?.()); expect(screen.getByText(/Map tiles are unavailable/)).toBeTruthy(); view.unmount(); expect(map.remove).toHaveBeenCalled()
  })
})
describe('citizen lists', () => {
  it('renders REJECTED status, reason and challenge link and paginates', async () => {
    const own = vi.spyOn(api,'ownChallenges').mockResolvedValueOnce(page([{id:'c1',title:'Rejected report',status:'REJECTED',rejectionReason:'Insufficient details'} as OwnChallenge],0,false)).mockResolvedValueOnce(page([],1))
    render(<MemoryRouter><MyChallenges/></MemoryRouter>); expect(await screen.findByText('Status: REJECTED')).toBeTruthy(); expect(screen.getByText('Rejection reason: Insufficient details')).toBeTruthy(); expect(screen.getByRole('link',{name:'Rejected report'}).getAttribute('href')).toBe('/challenges/c1')
    fireEvent.click(screen.getByText('Next')); expect(await screen.findByText('No challenges submitted.')).toBeTruthy(); expect(own).toHaveBeenLastCalledWith({page:1,size:20})
  })
  it('lists notifications and marks an unread item read', async () => {
    const item: Notification = {id:'n1',challengeId:'c1',message:'Report validated',createdAt:'2026-11-15T00:00:00Z',readAt:null}
    vi.spyOn(api,'notifications').mockResolvedValue(page([item])); const read = vi.spyOn(api,'readNotification').mockResolvedValue({...item,readAt:'2026-11-15T01:00:00Z'})
    render(<MemoryRouter><Notifications/></MemoryRouter>); fireEvent.click(await screen.findByRole('button',{name:'Mark as read'}))
    expect(await screen.findByText('Read')).toBeTruthy(); expect(read).toHaveBeenCalledWith('n1'); expect(screen.queryByText('Mark as read')).toBeNull()
  })
  it('retains an unread notification and displays read errors', async () => {
    vi.spyOn(api,'notifications').mockResolvedValue(page([{id:'n1',message:'Update',readAt:null} as Notification])); vi.spyOn(api,'readNotification').mockRejectedValue(new ApiError(403,{message:'Access denied'}))
    render(<MemoryRouter><Notifications/></MemoryRouter>); fireEvent.click(await screen.findByText('Mark as read')); expect(await screen.findByRole('alert')).toHaveProperty('textContent',expect.stringContaining('Access denied')); expect(screen.getByText('Unread')).toBeTruthy()
  })
})

describe('19a empty citizen lists',()=>{
  it('renders no own challenges',async()=>{
    vi.spyOn(api,'ownChallenges').mockResolvedValue(page([]));render(<MemoryRouter><MyChallenges/></MemoryRouter>);await screen.findByText('No challenges submitted.')
  })
  it('renders no notifications',async()=>{
    vi.spyOn(api,'notifications').mockResolvedValue(page([]));render(<MemoryRouter><Notifications/></MemoryRouter>);await screen.findByText('No notifications.')
  })
  it('renders nullable rejection reasons and notification challenge IDs',async()=>{
    vi.spyOn(api,'ownChallenges').mockResolvedValue(page([{id:'c1',title:'Report',status:'REJECTED',category:null,affectedPeople:1,fixedConfirmedAt:null,rejectionReason:null}]))
    vi.spyOn(api,'notifications').mockResolvedValue(page([{id:'n1',challengeId:null,message:'Account notice',readAt:null,createdAt:'2026-11-16T00:00:00Z'}]))
    render(<MemoryRouter><MyChallenges/><Notifications/></MemoryRouter>);await screen.findByText('Rejection reason: No reason provided.');await screen.findByText('Account notice');expect(screen.queryByText('View challenge')).toBeNull()
  })
})
