import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter } from 'react-router-dom'
import { api, ApiError } from './api'
import { AccountProvisioning, AdminQueue } from './admin-pages'
import type { Page, Review } from './types'

const original = '11111111-1111-1111-1111-111111111111'
const university = '22222222-2222-2222-2222-222222222222'
const other = '33333333-3333-3333-3333-333333333333'
const review: Review = {challenge:{id:'c1',title:'Unsafe water',description:'Broken borewell',district:'Gumla',latitude:23,longitude:84,category:null,affectedPeople:12,status:'SUBMITTED',hasPhoto:false,hasAudio:false,fixedConfirmedAt:null,createdAt:'2026-11-01T00:00:00Z',aiStatus:'MANUAL_REVIEW'},suggestedCategory:'WATER_SANITATION',confidence:0.65,moderationRequired:true,moderationReason:'Low confidence',duplicateCandidate:true,duplicateOfId:null,assignedUniversityId:null,responseDeadline:null,assignmentResponse:null,reassignmentRequired:false}
function page(content: Review[], number=0, last=true): Page<Review> {return {content,number,last,size:20,totalElements:content.length,totalPages:last ? 1 : 2}}
function show(r=review, initialQueue: 'moderation' | 'allocation' | 'overdue' = 'moderation') {
  const spy = vi.spyOn(api,'adminChallenges').mockResolvedValue(page([r]))
  render(<MemoryRouter><AdminQueue initialQueue={initialQueue}/></MemoryRouter>)
  return spy
}
afterEach(() => {vi.restoreAllMocks(); vi.unstubAllGlobals()})

describe('admin queues', () => {
  it('switches all queue tabs, resets pagination and shows internal review fields', async () => {
    const spy=show(); await screen.findByText('Low confidence')
    expect(screen.getByText('0.65')).toBeTruthy(); expect(screen.getByText('WATER_SANITATION',{selector:'dd'})).toBeTruthy()
    spy.mockResolvedValue(page([review],0,false)); fireEvent.click(screen.getByText('Refresh queue')); fireEvent.click(await screen.findByRole('button',{name:'Next'}))
    await waitFor(() => expect(spy).toHaveBeenLastCalledWith({queue:'moderation',page:1,size:20}))
    fireEvent.click(screen.getByRole('tab',{name:'Allocation'}))
    await waitFor(() => expect(spy).toHaveBeenLastCalledWith({queue:'allocation',page:0,size:20}))
    expect(screen.getByRole('tab',{name:'Allocation'}).getAttribute('aria-selected')).toBe('true')
    fireEvent.click(screen.getByRole('tab',{name:'Overdue'}))
    await waitFor(() => expect(spy).toHaveBeenLastCalledWith({queue:'overdue',page:0,size:20}))
    fireEvent.click(screen.getByRole('tab',{name:'Moderation'}))
    await waitFor(() => expect(spy).toHaveBeenLastCalledWith({queue:'moderation',page:0,size:20}))
  })
  it('selects a duplicate candidate as the original and refreshes after merging', async () => {
    const queue=show(); vi.spyOn(api,'duplicates').mockResolvedValue([{id:original,title:'Original water report',similarity:0.95,distanceKm:1.2}])
    const merge=vi.spyOn(api,'merge').mockResolvedValue(review)
    fireEvent.click(await screen.findByText('View duplicate candidates'))
    expect(await screen.findByText(/similarity: 0.95; distance: 1.2 km/)).toBeTruthy()
    fireEvent.click(screen.getByText('Use Original water report as original'))
    expect(screen.getByLabelText('Original challenge ID')).toHaveProperty('value',original)
    queue.mockResolvedValue(page([])); fireEvent.click(screen.getByText('Merge duplicate'))
    await screen.findByText('No challenges in this queue.')
    expect(merge).toHaveBeenCalledWith('c1',original); expect(queue).toHaveBeenCalledTimes(2)
    expect(screen.getByRole('status').textContent).toContain(`merged into ${original}`)
  })
  it('requires a nonblank rejection reason and submits the trimmed reason', async () => {
    const queue=show(); const transition=vi.spyOn(api,'transition').mockResolvedValue(review.challenge)
    const reason=await screen.findByLabelText('Rejection reason')
    expect(reason).toHaveProperty('required',true)
    fireEvent.submit(reason.closest('form')!); expect(await screen.findByRole('alert')).toHaveProperty('textContent',expect.stringContaining('Rejection reason is required'))
    fireEvent.change(reason,{target:{value:'   '}});fireEvent.submit(reason.closest('form')!); expect(transition).not.toHaveBeenCalled()
    fireEvent.change(reason,{target:{value:' Invalid report '}});fireEvent.click(screen.getByText('Reject'))
    await screen.findByText('Unsafe water: rejected.')
    expect(transition).toHaveBeenCalledWith('c1',{toState:'REJECTED',reason:'Invalid report'}); expect(queue).toHaveBeenCalledTimes(2)
  })
  it('edits classification, validates and categorizes through the correct APIs', async () => {
    const queue=show(); const classify=vi.spyOn(api,'classify').mockResolvedValue(review)
    const transition=vi.spyOn(api,'transition').mockResolvedValue(review.challenge)
    fireEvent.change(await screen.findByLabelText('Category'),{target:{value:'HEALTH'}});fireEvent.click(screen.getByText('Confirm category'))
    await screen.findByText('Unsafe water: category confirmed.'); expect(classify).toHaveBeenCalledWith('c1','HEALTH')
    queue.mockResolvedValue(page([{...review,challenge:{...review.challenge,status:'VALIDATED'}}]))
    fireEvent.click(await screen.findByText('Validate')); await screen.findByText('Unsafe water: validated.')
    expect(transition).toHaveBeenCalledWith('c1',{toState:'VALIDATED'})
    fireEvent.change(await screen.findByLabelText('Category'),{target:{value:'EDUCATION'}}); fireEvent.click(screen.getByText('Categorize'))
    await screen.findByText('Unsafe water: categorized.'); expect(transition).toHaveBeenLastCalledWith('c1',{toState:'CATEGORIZED',category:'EDUCATION'})
    expect(queue).toHaveBeenCalledTimes(4)
  })
  it.each(['ranked','manual'])('shows ranking scores and reasons and assigns a %s university', async kind => {
    const queue=show({...review,challenge:{...review.challenge,status:'CATEGORIZED'}},'allocation')
    vi.spyOn(api,'universityMatches').mockResolvedValue([{id:university,name:'First University',score:0.9,reasons:['Nearby expertise'],faculty:[]},{id:other,name:'Second University',score:0.7,reasons:['Water treatment expertise','Lower workload'],faculty:[]}])
    const assign=vi.spyOn(api,'assign').mockResolvedValue(review.challenge)
    fireEvent.click(await screen.findByText('View ranked universities'))
    expect(await screen.findByText('Second University — score: 0.7')).toBeTruthy()
    expect(screen.getByText('Water treatment expertise')).toBeTruthy(); expect(screen.getByText('Lower workload')).toBeTruthy()
    if (kind==='ranked') fireEvent.click(screen.getByText('Select Second University'))
    else fireEvent.change(screen.getByLabelText('University ID'),{target:{value:original}})
    fireEvent.click(screen.getByText('Assign university'));await screen.findByText('Unsafe water: university assigned.')
    expect(assign).toHaveBeenCalledWith('c1',kind==='ranked' ? other : original); expect(queue).toHaveBeenCalledTimes(2)
  })
  it('recalls an overdue assignment and refreshes', async () => {
    const queue=show({...review,challenge:{...review.challenge,status:'ASSIGNED'},reassignmentRequired:true,assignedUniversityId:university,responseDeadline:'2026-11-01T00:00:00Z',assignmentResponse:'PENDING'},'overdue')
    const transition=vi.spyOn(api,'transition').mockResolvedValue(review.challenge)
    fireEvent.click(await screen.findByText('Recall overdue assignment')); await screen.findByText('Unsafe water: overdue assignment recalled.')
    expect(transition).toHaveBeenCalledWith('c1',{toState:'CATEGORIZED',reason:'Recall overdue assignment'}); expect(queue).toHaveBeenCalledTimes(2)
  })
  it.each([403,409])('preserves backend %s action errors while refreshing the queue', async status => {
    const queue=show(); vi.spyOn(api,'transition').mockRejectedValue(new ApiError(status,{message:'Challenge changed; reload and retry'}))
    fireEvent.click(await screen.findByText('Validate'))
    expect(await screen.findByRole('alert')).toHaveProperty('textContent',expect.stringContaining('Challenge changed; reload and retry'))
    await waitFor(()=>expect(queue).toHaveBeenCalledTimes(2)); expect(screen.queryByText('Unsafe water: validated.')).toBeNull()
  })
  it('displays queue and candidate load errors with retry', async () => {
    const queue=vi.spyOn(api,'adminChallenges').mockRejectedValueOnce(new ApiError(503,{message:'Queue unavailable'})).mockResolvedValue(page([review]))
    render(<MemoryRouter><AdminQueue/></MemoryRouter>); await screen.findByText('Queue unavailable')
    fireEvent.click(screen.getByText('Refresh queue'));await screen.findByText('Validate');expect(queue).toHaveBeenCalledTimes(2)
    vi.spyOn(api,'duplicates').mockRejectedValue(new ApiError(409,{message:'Candidates unavailable'}));fireEvent.click(screen.getByText('View duplicate candidates'))
    await screen.findByText('Candidates unavailable'); expect(screen.getByText('Validate')).toBeTruthy()
  })
})

describe('account provisioning', () => {
  it.each(['UNIVERSITY','INDUSTRY','GOVERNMENT'] as const)('creates a %s account and shows success', async role => {
    const provision=vi.spyOn(api,'provision').mockResolvedValue({id:'new-user',name:'New User',role})
    render(<AccountProvisioning/>); fireEvent.change(screen.getByLabelText('Name'),{target:{value:' New User '}});fireEvent.change(screen.getByLabelText('Phone'),{target:{value:'9876543210'}});fireEvent.change(screen.getByLabelText('Role'),{target:{value:role}});fireEvent.change(screen.getByLabelText('Organization ID'),{target:{value:university}})
    fireEvent.click(screen.getByText('Create account'));await screen.findByText(`Created ${role} account for New User (new-user).`)
    expect(provision).toHaveBeenCalledWith({name:'New User',phone:'9876543210',role,organizationId:university});expect(screen.getByLabelText('Name')).toHaveProperty('value','')
    expect(screen.getAllByRole('option').map(o=>o.textContent)).toEqual(['UNIVERSITY','INDUSTRY','GOVERNMENT'])
  })
  it('shows backend errors and preserves entered fields', async () => {
    vi.spyOn(api,'provision').mockRejectedValue(new ApiError(409,{message:'Phone already registered'}))
    render(<AccountProvisioning/>);fireEvent.change(screen.getByLabelText('Name'),{target:{value:'New User'}});fireEvent.change(screen.getByLabelText('Phone'),{target:{value:'9876543210'}});fireEvent.change(screen.getByLabelText('Organization ID'),{target:{value:university}})
    fireEvent.click(screen.getByText('Create account'));await screen.findByText('Phone already registered');expect(screen.getByLabelText('Name')).toHaveProperty('value','New User')
  })
})
