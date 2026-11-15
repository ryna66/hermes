export type Role = 'CITIZEN' | 'ADMIN' | 'UNIVERSITY' | 'INDUSTRY' | 'GOVERNMENT'
export type Category = 'WATER_SANITATION' | 'HEALTH' | 'EDUCATION' | 'AGRICULTURE' | 'ENVIRONMENT' | 'INFRASTRUCTURE' | 'OTHER'
export type Status = 'SUBMITTED' | 'VALIDATED' | 'CATEGORIZED' | 'ASSIGNED' | 'PROPOSAL_CREATED' | 'INDUSTRY_CONNECTED' | 'PROTOTYPE' | 'PILOT' | 'DEPLOYED' | 'IMPACT_EVALUATED' | 'REJECTED'
export type AssignmentResponse = 'PENDING' | 'ACCEPTED' | 'DECLINED'
export interface Account { id: string; name: string; role: Role }
export interface Token { accessToken: string; tokenType: string; expiresAt: string }
export interface Page<T> { content: T[]; totalElements: number; totalPages: number; number: number; size: number; last: boolean }
export interface ChallengeInput { title: string; description: string; latitude: number; longitude: number; district: string; category?: Category | null; affectedPeople?: number | null }
export interface Challenge extends ChallengeInput { id: string; category: Category | null; affectedPeople: number; status: Status; hasPhoto: boolean; hasAudio: boolean; fixedConfirmedAt: string | null; createdAt: string; aiStatus: string }
export interface Detail extends Challenge { timeline: { fromState: Status | null; toState: Status; createdAt: string }[]; allowedTransitions: Status[] }
export interface OwnChallenge { id: string; title: string; status: Status; category: Category | null; affectedPeople: number; fixedConfirmedAt: string | null; rejectionReason: string | null }
export interface Review { challenge: Challenge; suggestedCategory: Category | null; confidence: number | null; moderationRequired: boolean; moderationReason: string | null; duplicateCandidate: boolean; duplicateOfId: string | null; assignedUniversityId: string | null; responseDeadline: string | null; assignmentResponse: AssignmentResponse | null; reassignmentRequired: boolean }
export interface Faculty { id: string; name: string; expertiseTags: string[]; bio: string }
export interface FacultyInput { id?: string | null; name: string; expertiseTags: string[]; bio: string; email?: string | null }
export interface Inbox { id: string; title: string; description: string; status: Status; category: Category | null; assignmentResponse: AssignmentResponse; responseDeadline: string | null; overdue: boolean; projectId: string | null }
export interface Notification { id: string; challengeId: string | null; message: string; createdAt: string; readAt: string | null }
export interface Proposal { title: string; proposalText: string; teamName: string }
export interface TeamInput { name: string; members: { facultyProfileId: string; role: 'LEAD' | 'MEMBER' }[] }
export interface Progress { title: string; description: string; dueDate: string; status: 'PENDING' | 'COMPLETED'; outcome?: string | null }
export interface ProgressView extends Progress { id: string; completedAt: string | null }
export interface Support { supportType: 'FUNDING' | 'IN_KIND' | 'MENTORSHIP' | 'EQUIPMENT' | 'PILOT_SITE'; amount?: number | null; currency?: string | null; description: string }
export interface SupportView extends Support { id: string; projectId: string; industryOrganizationId: string }
export interface Project { id: string; challengeId: string; universityId: string; title: string; proposalText: string | null; impactSummary: string | null; status: Status; team: { id: string; name: string; members: { id: string; facultyProfileId: string; name: string; role: 'LEAD' | 'MEMBER' }[] } | null; milestones: ProgressView[] }
export interface Match { id: string; name: string; score: number; reasons: string[]; faculty: { id: string; name: string; score: number; reasons: string[] }[] }
export interface Dashboard { byStatus: Record<string, number>; byCategory: Record<string, number>; byDistrict: Record<string, number>; open: number; resolved: number; rejected: number; universityParticipation: Record<string, number>; industryPledges: Record<string, number>; pledgesByType: Record<string, number>; fundingByCurrency: Record<string, number>; averageStageSeconds: Record<string, number>; stageSamples: Record<string, number>; uniqueIncidents: number }
