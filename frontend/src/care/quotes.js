// 古语素材库（偏旷达），low/mid 共用随机抽取；用户可自行增删
export const QUOTES = [
  { text: '人生如逆旅，我亦是行人。', from: '苏轼《临江仙》' },
  { text: '回首向来萧瑟处，归去，也无风雨也无晴。', from: '苏轼《定风波》' },
  { text: '行到水穷处，坐看云起时。', from: '王维《终南别业》' },
  { text: '莫愁前路无知己，天下谁人不识君。', from: '高适《别董大》' },
  { text: '山重水复疑无路，柳暗花明又一村。', from: '陆游《游山西村》' },
  { text: '沉舟侧畔千帆过，病树前头万木春。', from: '刘禹锡《酬乐天扬州初逢席上见赠》' },
  { text: '长风破浪会有时，直挂云帆济沧海。', from: '李白《行路难》' },
  { text: '人间有味是清欢。', from: '苏轼《浣溪沙》' }
]

// 危机页配语（严肃温暖，一句为限）
export const HIGH_QUOTES = [
  '一切都会过去，但先让此刻过去。',
  '留一盏灯给自己，也留一个电话在手上。'
]

export function pickQuote() {
  return QUOTES[Math.floor(Math.random() * QUOTES.length)]
}
