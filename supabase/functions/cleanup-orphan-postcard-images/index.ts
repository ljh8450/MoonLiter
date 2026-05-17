import { createClient } from 'npm:@supabase/supabase-js@2'

type StorageObject = {
  name: string
  id?: string | null
  created_at?: string | null
  updated_at?: string | null
}

const bucketName = 'postcard-images'
const postcardPrefix = 'postcards'
const defaultRetentionHours = 48
const pageSize = 1000

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body, null, 2), {
    status,
    headers: { 'content-type': 'application/json; charset=utf-8' },
  })
}

function parseBoolean(value: string | null, fallback: boolean) {
  if (value === null) return fallback
  return value.toLowerCase() === 'true'
}

function isOlderThanRetention(object: StorageObject, cutoff: Date) {
  const timestamp = object.created_at ?? object.updated_at
  if (!timestamp) return false
  return new Date(timestamp) < cutoff
}

async function listAllObjects(
  supabase: ReturnType<typeof createClient>,
  path: string,
) {
  const objects: StorageObject[] = []
  let offset = 0

  while (true) {
    const { data, error } = await supabase.storage
      .from(bucketName)
      .list(path, {
        limit: pageSize,
        offset,
        sortBy: { column: 'name', order: 'asc' },
      })

    if (error) throw error
    if (!data?.length) break

    objects.push(...(data as StorageObject[]))
    if (data.length < pageSize) break
    offset += pageSize
  }

  return objects
}

async function fetchLinkedImagePaths(supabase: ReturnType<typeof createClient>) {
  const paths = new Set<string>()
  let offset = 0

  while (true) {
    const { data, error } = await supabase
      .from('postcards')
      .select('image_path')
      .not('image_path', 'is', null)
      .range(offset, offset + pageSize - 1)

    if (error) throw error
    if (!data?.length) break

    for (const row of data as Array<{ image_path: string | null }>) {
      if (row.image_path) paths.add(row.image_path)
    }

    if (data.length < pageSize) break
    offset += pageSize
  }

  return paths
}

Deno.serve(async (request) => {
  if (request.method !== 'POST') {
    return jsonResponse({ error: 'POST 요청만 지원합니다.' }, 405)
  }

  const cleanupSecret = Deno.env.get('CLEANUP_ORPHAN_POSTCARD_IMAGES_SECRET')
  if (cleanupSecret) {
    const requestSecret = request.headers.get('x-cleanup-secret')
    if (requestSecret !== cleanupSecret) {
      return jsonResponse({ error: '정리 작업 권한이 없습니다.' }, 401)
    }
  }

  const supabaseUrl = Deno.env.get('SUPABASE_URL')
  const serviceRoleKey = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')
  if (!supabaseUrl || !serviceRoleKey) {
    return jsonResponse({ error: 'Supabase service role 설정이 필요합니다.' }, 500)
  }

  const url = new URL(request.url)
  const retentionHours = Number(url.searchParams.get('retentionHours') ?? defaultRetentionHours)
  const dryRun = parseBoolean(
    url.searchParams.get('dryRun'),
    parseBoolean(Deno.env.get('CLEANUP_ORPHAN_DRY_RUN'), true),
  )

  if (!Number.isFinite(retentionHours) || retentionHours <= 0) {
    return jsonResponse({ error: 'retentionHours는 0보다 큰 숫자여야 합니다.' }, 400)
  }

  const supabase = createClient(supabaseUrl, serviceRoleKey, {
    auth: { persistSession: false, autoRefreshToken: false },
  })

  const cutoff = new Date(Date.now() - retentionHours * 60 * 60 * 1000)
  const linkedPaths = await fetchLinkedImagePaths(supabase)
  const userFolders = await listAllObjects(supabase, postcardPrefix)
  const candidates: string[] = []

  for (const folder of userFolders) {
    if (!folder.name) continue

    const folderPath = `${postcardPrefix}/${folder.name}`
    const objects = await listAllObjects(supabase, folderPath)

    for (const object of objects) {
      if (!object.id || !isOlderThanRetention(object, cutoff)) continue

      const objectPath = `${folderPath}/${object.name}`
      if (!linkedPaths.has(objectPath)) {
        candidates.push(objectPath)
      }
    }
  }

  let deleted: string[] = []
  if (!dryRun && candidates.length > 0) {
    const { data, error } = await supabase.storage
      .from(bucketName)
      .remove(candidates)

    if (error) throw error
    deleted = (data ?? []).map((object) => object.name)
  }

  return jsonResponse({
    dryRun,
    retentionHours,
    cutoff: cutoff.toISOString(),
    linkedImageCount: linkedPaths.size,
    candidateCount: candidates.length,
    candidates,
    deletedCount: deleted.length,
    deleted,
  })
})
