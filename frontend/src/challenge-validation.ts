import { categories } from './challenge-options'
import type { Category } from './types'

export interface ChallengeFields { title: string; description: string; district: string; affectedPeople: string; latitude: string; longitude: string; category: string }
export function validateChallenge(fields: ChallengeFields, photo?: File, audio?: File) {
  const errors: string[] = []
  for (const key of ['title', 'description', 'district'] as const) {
    if (!fields[key].trim()) errors.push(`${key} is required.`)
    if (key !== 'description' && fields[key].length > 255) errors.push(`${key} must be at most 255 characters.`)
  }
  for (const [key, limit] of [['latitude', 90], ['longitude', 180]] as const) {
    if (!fields[key].trim() || !Number.isFinite(Number(fields[key])) || Math.abs(Number(fields[key])) > limit) errors.push(`${key} must be between -${limit} and ${limit}.`)
  }
  const people = Number(fields.affectedPeople)
  if (!fields.affectedPeople.trim() || !Number.isInteger(people) || people < 1 || people > 2147483647) errors.push('Affected people must be a whole number between 1 and 2147483647.')
  if (fields.category && !categories.includes(fields.category as Category)) errors.push('Choose a valid category.')
  for (const [label, file] of [['Photo', photo], ['Audio', audio]] as const) {
    if (file && file.size > 1048576) errors.push(`${label} must be at most 1 MiB (the API's default upload limit).`)
  }
  if (photo?.size && !photo.type.startsWith('image/')) errors.push('Photo must have an image media type.')
  if (audio?.size && !audio.type.startsWith('audio/')) errors.push('Audio must have an audio media type.')
  return errors
}
