/* eslint-disable */
import request from '@/request'

/** 此处后端没有提供注释 POST /app/version/diff */
export async function diffVersion(
  body: API.AppVersionDiffRequest,
  options?: { [key: string]: any },
) {
  return request<API.BaseResponseAppVersionDiffVO>('/app/version/diff', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 GET /app/version/list */
export async function listAppVersions(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.listAppVersionsParams,
  options?: { [key: string]: any },
) {
  return request<API.BaseResponseListAppVersionVO>('/app/version/list', {
    method: 'GET',
    params: {
      ...params,
    },
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 POST /app/version/rollback */
export async function rollbackVersion(
  body: API.AppRollbackVersionRequest,
  options?: { [key: string]: any },
) {
  return request<API.BaseResponseBoolean>('/app/version/rollback', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

export async function listVersionFiles(
  params: { appId: string; versionNo: number },
  options?: { [key: string]: any },
) {
  return request<API.BaseResponseListString>('/app/version/files', {
    method: 'GET',
    params,
    ...(options || {}),
  })
}

export async function readVersionFile(
  body: API.AppVersionFileRequest,
  options?: { [key: string]: any },
) {
  return request<API.BaseResponseAppVersionFileVO>('/app/version/file/read', {
    method: 'POST',
    data: body,
    ...(options || {}),
  })
}

export async function saveVersionFile(
  body: API.AppVersionFileRequest,
  options?: { [key: string]: any },
) {
  return request<API.BaseResponseAppVersionVO>('/app/version/file/save', {
    method: 'POST',
    data: body,
    ...(options || {}),
  })
}

export async function previewVersion(
  params: { appId: string; versionNo: number },
  options?: { [key: string]: any },
) {
  return request<API.BaseResponseString>('/app/version/preview', {
    method: 'GET',
    params,
    ...(options || {}),
  })
}
