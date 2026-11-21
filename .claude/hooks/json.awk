function isws(ch) { return (ch==" " || ch=="\t" || ch=="\n" || ch=="\r") }
function skipws() { while (i<=n && isws(substr(s,i,1))) i++ }
function endstr(p,   ch) {
  p++
  while (p<=n) { ch=substr(s,p,1); if (ch=="\\") { p+=2; continue } if (ch=="\"") return p; p++ }
  return n
}
function endval(p,   ch, d) {
  ch=substr(s,p,1)
  if (ch=="\"") return endstr(p)
  if (ch=="{" || ch=="[") {
    d=0
    while (p<=n) {
      ch=substr(s,p,1)
      if (ch=="\"") { p=endstr(p) }
      else if (ch=="{" || ch=="[") d++
      else if (ch=="}" || ch=="]") { d--; if (d==0) return p }
      p++
    }
    return n
  }
  while (p<=n) { ch=substr(s,p,1); if (ch=="," || ch=="}" || ch=="]" || isws(ch)) return p-1; p++ }
  return n
}
function unesc(v,   o, k, m, ch, nx) {
  o=""; k=1; m=length(v)
  while (k<=m) {
    ch=substr(v,k,1)
    if (ch=="\\") {
      nx=substr(v,k+1,1)
      if (nx=="n" || nx=="t" || nx=="r") o=o " "
      else if (nx=="u") { o=o "?"; k+=4 }
      else o=o nx
      k+=2; continue
    }
    o=o ch; k++
  }
  return o
}
BEGIN { RS="\001"; wl=" " want " " }
{
  s=$0; n=length(s); i=1; skipws()
  if (substr(s,i,1)!="{") exit 1
  i++
  while (i<=n) {
    skipws(); ch=substr(s,i,1)
    if (ch=="}") exit 0
    if (ch==",") { i++; continue }
    if (ch!="\"") exit 1
    ke=endstr(i); key=substr(s,i+1,ke-i-1); i=ke+1
    skipws(); if (substr(s,i,1)!=":") exit 1
    i++; skipws()
    ve=endval(i)
    if (index(wl, " " key " ")>0) {
      raw=substr(s,i,ve-i+1)
      if (substr(raw,1,1)=="\"") { raw=substr(raw,2,length(raw)-2); if (decode=="1") raw=unesc(raw) }
      gsub(/[\n\r]/," ",raw)
      printf "%s=%s\n", key, raw
    }
    i=ve+1
  }
  exit 0
}
