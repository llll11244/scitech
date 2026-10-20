/* 人民币大写(登记表合同经费展示;与后端 RmbCapitalUtil 同口径,元级两位小数) */
const DIGIT = '零壹贰叁肆伍陆柒捌玖'
const UNIT = ['', '拾', '佰', '仟']
const SEC = ['', '万', '亿', '万亿']

export function rmbCapital(n) {
  n = Number(n)
  if (!isFinite(n) || n < 0) return ''
  if (n === 0) return '零元整'
  const cents = Math.round(n * 100)
  let yuan = Math.floor(cents / 100)
  const jiao = Math.floor((cents % 100) / 10)
  const fen = cents % 10
  const secs = []
  while (yuan > 0) {
    secs.push(yuan % 10000)
    yuan = Math.floor(yuan / 10000)
  }
  let s = ''
  secs.forEach((sec, i) => {
    if (sec === 0) return
    let t = ''
    let zero = false
    for (let k = 3; k >= 0; k--) {
      const v = Math.floor(sec / 10 ** k) % 10
      if (v === 0) {
        zero = true
      } else {
        if (zero && t) t += '零'
        t += DIGIT[v] + UNIT[k]
        zero = false
      }
    }
    /* 跨节零:低节不足一千(千位为0)且已有更高节文字时补零,如 100500→壹拾万零伍佰 */
    if (i < secs.length - 1 && sec < 1000 && s) s = '零' + s
    s = t + SEC[i] + s
  })
  s += '元'
  if (jiao === 0 && fen === 0) {
    s += '整'
  } else {
    if (jiao > 0) s += DIGIT[jiao] + '角'
    else if (fen > 0) s += '零'
    if (fen > 0) s += DIGIT[fen] + '分'
  }
  return s
}

/* 元→万元两位(验收表口径) */
export function toWan(v) {
  if (v === null || v === undefined || v === '') return ''
  return (Number(v) / 10000).toFixed(2)
}
