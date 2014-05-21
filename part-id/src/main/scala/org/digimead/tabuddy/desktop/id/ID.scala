/**
 * This file is part of the TA Buddy project.
 * Copyright (c) 2014 Alexey Aksenov ezh@ezh.msk.ru
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Global License version 3
 * as published by the Free Software Foundation with the addition of the
 * following permission added to Section 15 as permitted in Section 7(a):
 * FOR ANY PART OF THE COVERED WORK IN WHICH THE COPYRIGHT IS OWNED
 * BY Limited Liability Company «MEZHGALAKTICHESKIJ TORGOVYJ ALIANS»,
 * Limited Liability Company «MEZHGALAKTICHESKIJ TORGOVYJ ALIANS» DISCLAIMS
 * THE WARRANTY OF NON INFRINGEMENT OF THIRD PARTY RIGHTS.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Affero General Global License for more details.
 * You should have received a copy of the GNU Affero General Global License
 * along with this program; if not, see http://www.gnu.org/licenses or write to
 * the Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor,
 * Boston, MA, 02110-1301 USA, or download the license from the following URL:
 * http://www.gnu.org/licenses/agpl.html
 *
 * The interactive user interfaces in modified source and object code versions
 * of this program must display Appropriate Legal Notices, as required under
 * Section 5 of the GNU Affero General Global License.
 *
 * In accordance with Section 7(b) of the GNU Affero General Global License,
 * you must retain the producer line in every report, form or document
 * that is created or manipulated using TA Buddy.
 *
 * You can be released from the requirements of the license by purchasing
 * a commercial license. Buying such a license is mandatory as soon as you
 * develop commercial activities involving the TA Buddy software without
 * disclosing the source code of your own applications.
 * These activities include: offering paid services to customers,
 * serving files in a web or/and network application,
 * shipping TA Buddy with a closed source product.
 *
 * For more information, please contact Digimead Team at this
 * address: ezh@ezh.msk.ru
 */

package org.digimead.tabuddy.desktop.id

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import org.bouncycastle.openpgp.{ PGPPublicKey, PGPPublicKeyRing, PGPSecretKey, PGPSecretKeyRing }
import org.digimead.digi.lib.api.DependencyInjection
import org.digimead.tabuddy.desktop.core.keyring.KeyRing
import scala.collection.JavaConverters.asScalaIteratorConverter
import scala.language.implicitConversions

/**
 * TABuddy identification information.
 */
class ID extends ID.Interface {
  /** Universe public key that separates sources of information to friends and enemies. */
  val universePublicKey = """
-----BEGIN PGP PUBLIC KEY BLOCK-----
Version: GnuPG v2.0.22 (GNU/Linux)

mQINBFNnXRABEADHsryTWeUlOJPBSlHxa61B1ESaQiUuSfnNUL4GMpZFG4RMxWKU
jy0SEZ/Kb/syxpI0L7L3OSDpRK557CYt7Y/lHMSUguz88VH/dpKe4RMGUD6LexxW
dDEOifnm/kt3A8iF+LgG/YBOTX9euLn6LrAXEKS8DlpzaoKfe+63ogce/zXD6VWK
r3hMo2/2jUpsDuLxrEqDgqcdt9/g520La+O8Wc6w19xo6jQbnnS1NDPlwv07qE8y
DJyXLb4DEiHIe/t5R5YFhgIz0O2IiXLZvhxKBsNGE+8DV+MnAZlKcwEOr/E3qkis
pudlXAykVrefrO2ToXJ7jIaacuC9PAoA6yrvlMV33fXlBY4uhJ4b5ETPtKaLBgtj
SOzGSDfJ4C7FBRU+uUsU9CBLP1OMBFW5YLa3R3aBRjXdKLmPKHKbleywt1xdiR5k
KGN9vAxP18U70kxzjJf0pkV1oSh1xcm5lNFMeIELeP3Ym4R39xzc39iAws9P0mNG
3xEOgmrWpCKfC27vMZkjjyjeVqwjEKS4mfxxpHIBxQwHJk0GlF303I+y2ajFS+6V
cxpgRxmzHGGU6ODvzMjeMblzXn8s9qlli7kP2xmzy/V+YnOUkfI9M+04yFhuIviK
/JmG1OIODLDEVlKHPy1hFvAO1MjXG0R5pxNbEaMDxncWlkXdeKzAP4mW6wARAQAB
tC9UQUJ1ZGR5IChUQUJ1ZGR5IHVuaXZlcnNlKSA8ZGlnaW1lYWRAZ21haWwuY29t
PokCOQQTAQIAIwUCU2ddEAIbLwcLCQgHAwIBBhUIAgkKCwQWAgMBAh4BAheAAAoJ
ECrOEhrYdeEtMToP/iCi/KkAyL+q5NQZIB9/0tCyMhXjDH8SpoB9PjL2Cdv4oIrh
dnbAzTiLzVXJptJhO02kGdtuOK0zqJgGap0EB6BJ9qamPMKOke+9LeXM9z44bmXe
NNCmjJI1WX5+PJ7OQ6iFBWIZSZdrcQZYiaZMJruMADx/xgXHhUs0TfovEdBtzRWM
acIGVByv+Rbr3ONUweiBM7MA868z9rVYEhGTsJNLKtQ9++gmGGsLa/Tnl7QUVihA
hxGkZY7PYwxKKTbQA4+5qpEgXbxa9Dqn1FRtkHJ67jgUA9pkAtH/oQ3k88NEOLcc
Y+D4cvgVEWRqiULJK6XQAtsmWNxuqaQri5K87mP0bLrCK9YPC9y5IzKZAf4c9p88
Jd9kkQlIRDqCGtTIL87ZTRC0rDrHj9L6qD2bWYkBJqP5xDazjkabnv9wsYiQ1M9C
tDumvkW6EK+PlT3/oJcHIvO+FUZOwX6s3YhSKUHLZWCqfV2nIfHQ7afLaGPeuSqs
XTePQcnPROBSd7OYwCEpSKcL+d8MDvOb8nWBinw9dIwKDIiaeypcBp8mOGnoaiqQ
YP+mSwN1hOGbXTBApBDMv9AKK2XDSRaCt046/F395rOuINcmZTcupTQfjaeg5It0
sGAxQYVbR+rhxyanCxTVSmyLxsn8YEmMRyp3E7zmOtWXCDr5xQ4LQmgPmuBdiQEc
BBABAgAGBQJTZ2PjAAoJEBF0PdBYOxOOn0EIAMF5uVYXlpwHWLSpVVC4aHysEz0m
m29uFxqSbUjCDAG8uLOmCNCa38Cm/3Vf1/nl+gWKO73MnElczZ8VrMSVPtzf+0Af
6bni01wLvvP9FHHlUyCbt4USVc0i6VtfHnMi4kYVSM/wAbKygpvy3BkM8CBeNK5f
PDMOR12ssCQ1TmuQfJFOdGJSAukvbnwWmMP5WqpZMff8zD6DJkPRT0KwQUzCwuj8
qEXi24KQhnkht4QF8/SWGr5sT7Tv1IUTH3uZcExWuUjuyphUDDoSJ+tKmWG/6LTV
RKN9b54ip2Yy7CPA1QleDlUmwV0m7y0WRLaDi6hULN62CxrHeM1YAsYVCDK5Ag0E
U2ddEAEQANrTwFXdJtE21TxiTwsQIAZCForigdg4bTmhhCfzJ+h4Joqs37gJ523p
Ha6fhkxT1UPJ08TKNw4TCsF3XHsJ3ARwrOUFCZGAbWBZLCSwj+PJZ71vSM4Tp0ww
YbelI8ZKiDjf0lnO6FsdpHepRy3UmpFPNSkMd0FfsV7CsWkZWTXHXQnbpCHaix5V
clYBkDJanlWp2WcoYYXrh7pXGypM1I9vy+DIfE5/oiz3msYzK57po8lDBMTFYE2h
zxt2ghTCSDWvxuWffq5NA2PaLOG5bECu1QlAG0cmKfFwX0yUM8zcK1hOB5z9qF9P
6yqxPsYll7z2XIFB5dlAdZgg+aq5rvdEWcOap1qocGsi6lbULuJYBoXWhen6Irj8
e6clJvGoHY417byFgi3uA/KWwSRBaFTBrrGxw/3AlaKiAxx9esXJllFBadeEgx7s
W3d1W8e0EqKsVhJ/MjQK4E85fpVHyArkibgRZkISnkw4PP8BnhUu9pSA60obSOgR
CDo2C4488XkZeY239UBNo+dSEDxP3622kM9PvO24M6V4bFzUJsRrFZZgKN6udbHR
e17yaOBXCkkyyskF1f7e2x8AlP6GnXQOp5IYqQD6fbhIE1M2ijxSOzvxx1NvxHVv
9eG1Qe37R5f2bJhQlH/wGge6j89PTM0/8J5dEWP2AQ+PpjIL7f7pABEBAAGJBD4E
GAECAAkFAlNnXRACGy4CKQkQKs4SGth14S3BXSAEGQECAAYFAlNnXRAACgkQRZ1W
iBtHq7S04g//bfLjdjomeyhEBbEVUyc5FFBUy/Pc2OUxtS7SiHlHFngAbG1mxeCH
/Nv8yjJgYwIvY4y+LOpizRN0iryAd2LTDEXVCIyqCtPq4ZA+njHYgTUuJva52O29
m4D/VwuVedZNIbkWoBMaANyDEMty25Lr+dym9gLaRsXeXrG2pNyairnuaZXR/hqt
74Dd+Q8z+kMAnrpzgC1C6fhYYz5pzoGWjdFuMF+BZ3MJ6q0uFoA4MlznLfqynPRI
Q5jJHKNvGRHnk45TN/7RYYU8ANVKFPbyHKXF79qJpw0piBfyY19OTTZ3dU4XlBMU
daWjxegVBDaLWTAN5G79SX5UjahP9nKFOo8IH4AJkcnVhsIsp8UD8cwACFYfosdk
ItD2Q3BawVrGzC0GD8hrQ6KV6s4eoOGgFq/XoiCs9f4lMo3AN0J1maWQ4L24WIg4
6VXiWI1eNPIvQd962PagKGtF4DzTKpsZmzTWORXlUz/5crjLebiopGYQPTroVqhf
PXhDkovMqJ/mgmmIbpP2IQ218j69Gh1OlMjZN/78ZavJKh0WM46XieiVaVk4A8rV
5T4ePaZpsMt9St3OqpIYAsfpKoe5I39H9hC6QEJ5hHJ87uNeoZf/OUSct70WvDmI
WWvte/wKaiTgAbnSdHz74LhUkcX/ur4eVWO9tfB4MmZCwX9dp327HQhVOQ/9F8EE
XCY+f/zFYREybUsguNSGzoLYmPX2AsytANQtMaDNduNXf9gopzMzo8FTrlHPyk3I
bzqHie9dEia7u3+n+8aROZMPwZ0jO++TD0PZ0bbmHMVL3K/xp2lAC3/Zu1oYim8y
Sn5Pb60NtQBJIhwwCsxVSzlr1/K2nW+0u5mCd3QUpqDZiimgkF+/FAdjPRm3Jy7+
o0WOs+1UnwBUnFqufl8xfgrFYRDGXGHozU1VyWZIH5EfbebW4zfzJMBb6ikKZgRe
4l9SMMMWsYxqiLbsdVHjj3MTovTKEYiF6k219JsJwLdxRBaQVff/4QScEO6t5Yzi
2HzMBC1AjpctDEWRxA6edtzCinv9q4V6RiMzXMr5sRbKWhsttvkOYzpzeV63xc3u
AdMZjdf8gdy5RM76uf4Ldbu43xzaCBKkFw1DydTLzJ6aSgbWw5hnHI6Gxtl1AHPA
MZaSl1Z65pR5I2peCGqUm/IYwVHeapLNCgHQXtGzH0cTIVhtkfuy8P0Ftm19PY7y
qY6HG+QPs6jojnkIaDUo9hkSAhrvXM4+IuhdMlv0frr8hfjEEM6Q+bmEF3xGxZls
LtCQGKq1piWYnuqiQca0JhRbql+/Xsw16YZyWdLfNJyGHJVk3Z2OTbZPMPEl0272
C0/8QdbSltU2uw9MD1+IHNz5PaK4uGGdVO9r1oU=
=7Buk
-----END PGP PUBLIC KEY BLOCK-----
"""
  /** Someone's encryption key ID. */
  val instanceEncryptionKeyID = 0x49977B98FA9EA7F2L
  /** Someone's public key ring that is known by everyone. */
  val instancePublicKeyRing = """
-----BEGIN PGP PUBLIC KEY BLOCK-----
Version: BCPG v1.50

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
rUn2SAF8gor5FaLPF1BN9Xg=
=GAXd
-----END PGP PUBLIC KEY BLOCK-----
"""
  /** Someone's secret key ring that is known by everyone :-) Yup. */
  val instanceSecretKeyRing = """
-----BEGIN PGP PRIVATE KEY BLOCK-----
Version: BCPG v1.50

lQdGBFNvTwsBEADRRQvGR9KFd4CF+3aWdOaYiTKsq1yzTJniXkkS7B6CES3Mm0xM
PNxPF/KpU8ciNi2vNRQ2UfzbcdyYFZiU72GJDslkFI72Ih9WNGZtXDSvApYx7Cl2
e1U7VExFJJ1T0NF8V/3KU43B7tUcxKzBIJBgUMfwIdEV5SznaJ5GwbzeQzIRtjVZ
1LwQ2IsfPysdIojukWHnPKgeLgj+r1cVgG60uQIB6jZMhVk+BNfqac4XKXiW0TsZ
2wo4xHbi+Nskot8wpk3oxZt+JIB5SuhCEKT84sl+9sFFbR8L0ITemPuaWdTQeWI3
vv+PmCeEvUb9VpGjcx3Yf8iTYytb2THZ5PSYLGSh8poq+GpHmMaYpNOwGzxNfJ8T
YnLmvkZTw/vMJqZ57BIP1WzHFu715S0Yp0QZSp1OU40SQerv+gqrRH0400P9lZBB
CUhUcEegcvgkTM0GVKLZw3UQqKYXfA17St+561NFJuk9uuHv3dCmIF05zo6LVLtT
BrfvAGUB8GY0IXkEGkR6ActM/3cFTfSU84OjWoxga9mnvApAw+jhX1Lob049IFQK
B0bar5sIRm4ey294XTWG62uJUkmVqACulF/XG931MGegaE6QX8ZXUJhpMi2guxiF
N7ItfEGZ35kRXvdwYr6maOJpgyMfMJ8ifzv/F6GhtJAbi1ax1l9oUXb/vwARAQAB
/gkDCLS9Pd4KuuSPwBSmdzrIp0MgMDPIYLfV85TTSU55LvlMKbsqtFba9VTXpW28
XsKA9gC0m1iABah968043JM8/D5Db6Mfd23/RoqefvI+QgrQI83aDX8V8WGaUEhQ
yDpk5q4dhzuzROCxY36opaDHFjEXgDX3ctaS6eehJfDuRcg4d/TuExef6LfgTeYy
8TT0etxiag08xDu33KnqaoxPOnOYTeJn3j8Ia+viquXjFC5VkgFaFZ3aK+KGkklg
jtT8Ya4hnOx1zX0VtI5RW2iDtwifdgn3DvI7v9Z0Z89pkQR9d4qNLI4eCfSdsBkl
aHn1hfdiqY/vK+/28Ixe5/zSsMPxcZTxMT2yA82WwCOcV7ZIlcL5cbMv3/7kmz6T
/t4eU9Dm5TEeVQT3IMb/z23e9qKcN8Cb9dYlkA1M6mwWxiOgV1Ixko5spi6G8GMA
faqObN4pFe/dXWLQNn8hvsiHBPWWnZHsgf0AyogU7Jjm92BYmAZ1rFyCVarhsBv7
Jk9qmVyMuhPCbn1YyZLg+wEjEyvxIUTVs/DsWBfXtTN1pmG00zN9032QMT2eyZj8
6r/3ceAwJo6E4XnTbk3NcrFdwpK0YQluynFLMSDMPJCq7phQhk8lmD/p8q8/fklR
d9EZoJlUJRnCZz+fy+OBgbRRdc4V8OXBCVG3GBMuXVrNLoxr8YM4Demv1MGRTzHP
kWVhjAplYa1Zdn2zGhRBqgqS+MWb5CIsUvNrLEFihUtij26zwmhT808cnVJIK6B4
n/CEw+IraIgZl7Q5LNlH91tfm3W5SUlmVASH07cy1wlJaO2rGX/1CYDo6UKVb3NS
CS/ucxRQpxX4Z1+ppLs2b5j0gsQoyuvlaLyznlXI/uVMzAzOYg6B6ZrZ4PavX8gH
hnlZKRrHOyrTcTTYn44/LjtdI2+3kuuYd+NiCBx06j7eSJgGwRacwAei8I7xjbot
oxtnEu/J6o0jCH1gUgzNR1zlNenmGGcjiPhypLQAtlonApe8LvzFcCnvWn/uxNdU
ZxWNCyFtaAEtrVJXQvSpb3gAjI1AdXrIOpBxpEqrYZ8ShFYeLbCzSIXXCtniZq6u
zjFLSAAxT3YSmkJ2InBi7SPkxdgejv5icqGGKz9MMlCkbGl6nKwx4nALcmdas9qS
R+A7qG1qFy1W4FiDnGZjo+FINf1/o5mnUtNNEWuefwHGM1lJNGuKO8FMHo0mPUVX
5zYb8c9pD+ikDjvEIRPi2PUp05IpBDUlYhxEKP2nQVEVuX9cUkfmYwsgi/WH1S3r
fdqQkqWJ/u6+VbjLyiwdaT76PQg/g7vcIwdvKRWZpANBZtHAQvQRpi6pmQOXpNzP
+tHBZjs0xKZeWMvUd7K1CNRHnAvJzYXHhpoumjdsb0APG0y6+COYSXr1cuhjY2N+
C361oLeGUzMITY3LtW2iXxB+fZd72YBAv/V6Td4ghOiE55hwk3SLoIjiKd0jA94L
EH5Zh8KJm525Gi8Hy9+UIluYKpD3l+yqcuYiRZliw/0nd6rTxgPV36MiMn9CvpZL
/orpR4lw8Yj2POGTqNznwX/Rmt9FJmXAcj5GJD3ZFuTbp2Ajuh41I3h+y9s/ta86
aLMFBeT1tQ62wdbzEzbGW4YUUObBIFmPyi3bPZNYjqyU8Hx+N/5VGa6SCd1P6OOO
H8CYf9Zs6yEueQLOeuHYMh2fTI1g1594UtY68Vqn/KsDX1X+PLJBVQ7uffFFcf8E
XaUXFmo7r110QbtUty+QSeRM259k1QMC9CCtqbC+b1UxY9/CNXTeqXe0LDJhZjhj
MmQxLWM0MGMtNDM3OS1iOWUyLTE1NTQ2NDg1N2JiMEB0YWJ1ZGR5iQIuBBMBAgAY
BQJTb08NApsDBAsJCAcGFQgCCQoLAh4BAAoJEAOWfN/a5Q3QwOYP+wZAI+n/Bg/x
ZAk7rn8gMcyK0t++1T0kORmhUfNnNiAy43NadV679lo1Ymz8WMGYKslPwz7xoGh0
e2NgDTU5tp5Fdgw6OJaGySoouw2G4y37zGsueyHeiOFadi4iaf0kAyXppYoniW6S
+3J5S1IpKYVA+5LyCnoUD7TdKGpC8SccynWOOQeiPeWJHhJaJVoiDZeObdI+yR4v
GbJ0ABY2mZm7c4h2jJioaFVPigZX2U8PV4kbqZlRhXlUk1ugV8vjb6JTN2dpkZ6k
2+RIrMWfKoA/x6EWVL844AT9HjrGvD0zKOhWWlbCL5OvCyJUDtk4Gaf03O8+3o2e
iKFtMnnlIPKhtfpkxmc9+B7s2S9MPCsR2zXY9cDbV457shdH0kf6BkLLuOwIocQd
4IazyhN78nErT5R+mvPOnmq0Ok8WJ1Km0vzL3Es2RoCJ/aNQ6p1A5SLvppyxGks7
hLPEv9/Ti7/OicAID45rZ7V+GjQvEmlpciDNsoTgV6a7NrUaC3Hy7RVkyVTXo0+I
QvVe2e53bbhnItvIGQbKj88MCtaeW4zyUiVqsa8Qj/JNJlI3MqZPMW1yQYeDbB5C
RZufA0cJ8iA1TOlWgt1So3IBTsu1eGwoDVkmTh0j91b29Cp9KeL49XcWSOifrMCs
epEUnBB09joUKlI5+9+ZeiLC/RMIDA60nQdGBFNvTwsBEACYQbuVVTFRRdIpLa+y
WH4Mp/qgq0vKWI5/oz5ZYVJIlD0KyFfps0EParaBO92z0VYD/ECheerp21XCNbDU
ZOURf7pA4DwCseBdEVUZB6rVKVT6PFHqpDOBiJPsDd2YyKzne62Ruow2+KGItrjY
wH4ProMa4RGzq7eF8TYsvjjdtbmKpHUYzd7TFnroZ/367ieKIVHBOTHeCBvZ/+8Y
MXiGYPErEiBrAC/c5wHiq+sOOtoWGoFPK8bRvZ02/CeajkbDPNwFEkgazx4H6HDC
mwUuS1NUS0aRZaaUcDXiGSznrHubhWY+P4JVJYTYwzpuGlaBdDI9z+pJ6hEjxO+4
0sXpxii7TpPjFJCn0j7oYIYTdPjTMITnI2NHzaoVlPcUuRgxMfPOt6L5wdz+Y16I
usZPJGyrB5R0S7zgf+Ef/gxrdt/NvRyP7yhBxhi+fAv9uu3TsO0pN6LaUZ/79oI4
GQtosJDxPpRk7tssh1TnReMIXacJFR74QwXgg452n+c5QHGKfHXL9YmmGn36jhaf
U+xVIQboshL/WQyxvy7YkKf2L0BoBM1sT3Q41PRiU5ekVs6rmJONprT2q3CNh+Oy
963B58Eqf9nj1O2bgJV8vhK/xUUUnL/jZaEnh+4Nt/JOLi5utQAJlPevdnmU3PyZ
JLHfEMcQuZqOziybAnXKSdYb3QARAQAB/gkDCLS9Pd4KuuSPwO+Wr1HlUyr8oHjL
qZ6S+eh1QpMsvnijcCmXWzk6GjN67AvXNoLrR76sJJ+GaId98D8ronSE+txKEhVX
/QWNbdUygeEuZ2dia7KU30kbBUyk1n71M5Zixd/xW9Q39y6NTgCRoC4+qT4IVSVi
hdbTdAnA18zKW4XgsHjMnxF/g51Xtse7Kd+rO4vlOEXlAlPJ9pOkfx0srW4mRU9t
90JQJvxVUpgwMLvO/AdvmQRvPQkrq4trCU4Pmp9L+ocuqy9ELrGOSf88Vl/jRXVl
W/ldO1Eh2EmZC2kQ1zgNmFU1y6cCnJm6jhCX1JLfmZpqnneddQzYzQYla82v8nI2
T+LkEjplk/yvKppsb5ZzjtAPINkrTAbh9YSxLI2zlWP+ySzOI3BkXs0cUEf762PL
vnZ5+wqoyyMtBQOwoYo8rr/zccySqrw6Cx+RfZ/gExPb2hZL8JghFBbBTKICcChM
Kjpqv6Q/vtoUt4s0yN6INz/DEXxl7hzqimVo9Yc54bJTsq8pFNgVj8LJPXpVnZc6
hFYVtH5OwmIUTMSX0NqZWxKpnn5OKVeH7PqRHxgzMD71C6W8DcOK6L5fcljfWXPn
ZGq9wn1dGL7Na56RCIj0aFUVVCYBBMHz4Vhr9pTNB2rmknGo8DjQJiSaGlZOEJDU
VvVSY61FujoEV3d/GaUa97aTpbD9JAtHhJV62/tSdQAJg+T2gUs6bH/2tp/R7EYw
55WZUM6gvqQAKCK2fUFZXyIL1V2aoD+KnJLN95WVmurcV/8lx+wkXtuwQqWgiTZY
8QpO7CFMdMaOPzGwEZo6S/gsBuFGvale0bv7l5F113R+Xf1rYUc2fHluLPaaQ7DF
6zXjxbsTkuqgoOc5ARJ7NpHVtORUzw5K09+46HNejoP7oWiW3p/iSSUUCoO6JriE
xb8ytnTLA8crM962EYMDpgx4nKJ9YU33lz9pgsY1GAStow3kmFmcmzEYpPJBi0Hc
ID9yZd2qxfnLxxU2JpnpojaXoBO45pi61nU4ag0KiozLrKlwQQSu3FFn2wcYPF9M
ZTrCDWU3HEg9OJC7LTDMlI9MhycKenPeP0gjg4+BCt2qAuDFbhCCIqRv7Z4ROWOR
zeXtffrb4Cla38vKpM9ivwZNbENSDiaY3Vc+l1EPKTLLrMdo5Z2PUGULR7V5V9SX
jvxxaK663m4rkWfEcKbV14lf318M8AEOjfhavFvdq4+HkRTNOmvg61R8E7WNAvkH
AdF/p0/ronEJC+J/KI7b9ZHITKFT8rhmFMeMViumqJirNWpLhIbD/6BX69mmT8jc
08MPpj9m45UZVADxs+pYzXyQZz7TrJWliX3gN+TjHwy/yoQ65z9P0v4tPGEd9pjG
driBB1NGwLedHvWua9cT6Ug2Hg+hT3R71LJ4yc7VApf3JZW1IclUUG0ldOjV0cRC
91ZxK0bXngC4lJe8nq8bJAO5qWsKGTRIRZJGl9OKmeIkNfgkp+6whmpM6rBebRGt
WaoNrZ5jx2oGpbBYQvewfzpCBFbZ+F8M73lBQBA4ari2fXLD4W6rB/TzKR8ICN8m
gr+SLVgjjfuLOVXrN3phu4addBs4wZV0wdZstyD8G9+8Ub75iVyRoTkixQ+QEtA0
mq0kHTPrSMWHFH8w2uK5/TGLTEtl9ZTxjZPqg4KM43Dyx2fbgp/eWQKv1s4cLZOt
1VL5qLyf2kJX2WAv0D6CvSb7YheyrHffzOxRVd6vq1p5oXXGYJx01aUpg+97L8up
c7JjEvDXDVriZWKS1wjkZ4eJAh8EGAECAAkFAlNvTw4CGwwACgkQA5Z839rlDdDx
DQ//VoN/xU0urUkJpZiuQvNRKkoHdLEZLgUPz8Va9b/N4PjKiUmm14Gz6y/Iys/0
zhIhPO0xW2tzpPQAvSHE5zaDWzcZAR8nKgy8V3uzMebig3b1EzRQIoRGPnK9E69Y
MUGsbbXrvxRn/Tr3TXRPCp7rRQUMjx8YQuEspk4zUZf5/vkeyPuzT7A+aP8av+xH
O7MODuDOjlgwBiK7uZyWmagBvmCkp4gKTYerm7jJ1QMssxTLwr+LewgVzcCEQopd
UhMaS4ZKgpnw1btRdNaFa3empzmmeAi4b3vt830R3HbZyzimTu3sQ1QVtxhmmsh1
mLi+eqKOoOFa/ejxFAsLaMp+ZfdmMIg5LY4kLaZ7U5OiFK0Su+dJWBJuDmoVDy/Q
D+ZNcYTNnWIltkPq8+mTXmSlObefAUZGmSOEo739vzK7PYfPUPBR1DWKSrKtidUt
1SoucIRhYCkc2mpnYhDw/oWe+t1wIrWKX7slpr9U85vjxiEaQOd3qS71M+zC0xN0
Y2pM+GeAuHBXn8FFwlpy9S6JS53nzrqi0pF8uw4Y3BmwwYgXt7gt4+jNzKWEw0lp
WM75T8ewjXb+Qx3HDrBob1KjfZ+kp77XgW9DcS7o9nU35vnqKiVor9DEasLG/4kD
veJ0gKLsrlvLjLBVU0VinVrIrUn2SAF8gor5FaLPF1BN9Xg=
=vSaf
-----END PGP PRIVATE KEY BLOCK-----
"""
  /** Someone's signing key ID. */
  val instanceSigningKeyID = 0x03967CDFDAE50DD0L

  /** Universe public key. */
  lazy val thatPublicKey: PGPPublicKey =
    KeyRing.importPGPPublicKey(new ByteArrayInputStream(universePublicKey.getBytes(StandardCharsets.UTF_8)))
  /** Encryption public key of fuzzy group.*/
  lazy val thisPublicEncryptionKey: PGPPublicKey = {
    val encryptionKey = thisSecretKeyRing.getPublicKey(instanceEncryptionKeyID)
    if (encryptionKey == null)
      throw new SecurityException("Unable to load public encryption key: 0x" +
        encryptionKey.getKeyID().toHexString.substring(8).toUpperCase() + " not found")
    if (encryptionKey.isMasterKey())
      throw new SecurityException("Unable to load public encryption key: not a sub key 0x" +
        encryptionKey.getKeyID().toHexString.substring(8).toUpperCase())
    encryptionKey
  }
  /** Public key ring of fuzzy group. */
  lazy val thisPublicKeyRing: PGPPublicKeyRing = {
    val publicKeyRing = KeyRing.importPGPPublicKeyRing(new ByteArrayInputStream(instancePublicKeyRing.getBytes(StandardCharsets.UTF_8)))
    val keys = publicKeyRing.getPublicKeys().asInstanceOf[java.util.Iterator[PGPPublicKey]].asScala.partition(_.isMasterKey())
    val masterKeys = keys._1.toIndexedSeq
    val subKeys = keys._2.toIndexedSeq
    masterKeys.foreach { masterKey ⇒
      if (!KeyRing.verify(masterKey, thatPublicKey))
        throw new SecurityException("Unable to load instance public key ring: untrusted master key 0x" +
          masterKey.getKeyID().toHexString.substring(8).toUpperCase())
    }
    subKeys.foreach { subKey ⇒
      if (!masterKeys.exists(KeyRing.verify(subKey, _)))
        throw new SecurityException("Unable to load instance public key ring: untrusted sub key 0x" +
          subKey.getKeyID().toHexString.substring(8).toUpperCase())
    }
    publicKeyRing
  }
  /** Signing public key of fuzzy group.*/
  lazy val thisPublicSigningKey: PGPPublicKey = {
    val signingKey = thisPublicKeyRing.getPublicKey(instanceSigningKeyID)
    if (signingKey == null)
      throw new SecurityException("Unable to load public signing key: 0x" +
        signingKey.getKeyID().toHexString.substring(8).toUpperCase() + " not found")
    if (!signingKey.isMasterKey())
      throw new SecurityException("Unable to load public signing key: not a master key 0x" +
        signingKey.getKeyID().toHexString.substring(8).toUpperCase())
    signingKey
  }
  /** Encryption secret key of fuzzy group.*/
  lazy val thisSecretEncryptionKey: PGPSecretKey = {
    val encryptionKey = thisSecretKeyRing.getSecretKey(instanceEncryptionKeyID)
    if (encryptionKey == null)
      throw new SecurityException("Unable to load secret encryption key: 0x" +
        encryptionKey.getKeyID().toHexString.substring(8).toUpperCase() + " not found")
    if (encryptionKey.isMasterKey())
      throw new SecurityException("Unable to load secret encryption key: not a sub key 0x" +
        encryptionKey.getKeyID().toHexString.substring(8).toUpperCase())
    encryptionKey
  }
  /** Secret key ring of fuzzy group. */
  lazy val thisSecretKeyRing: PGPSecretKeyRing = {
    val secretKeyRing = KeyRing.importPGPSecretKeyRing(new ByteArrayInputStream(instanceSecretKeyRing.getBytes(StandardCharsets.UTF_8)))
    val keys = secretKeyRing.getSecretKeys().asInstanceOf[java.util.Iterator[PGPSecretKey]].asScala.toIndexedSeq
    keys.foreach { secretKey ⇒
      if (thisPublicKeyRing.getPublicKey(secretKey.getKeyID()) == null)
        throw new SecurityException("Unable to load instance private key ring: unbinded private key 0x" +
          secretKey.getKeyID().toHexString.substring(8).toUpperCase())
    }
    secretKeyRing
  }
  /** Signing secret key of fuzzy group.*/
  lazy val thisSecretSigningKey: PGPSecretKey = {
    val signingKey = thisSecretKeyRing.getSecretKey(instanceSigningKeyID)
    if (signingKey == null)
      throw new SecurityException("Unable to load secret signing key: 0x" +
        signingKey.getKeyID().toHexString.substring(8).toUpperCase() + " not found")
    if (!signingKey.isMasterKey())
      throw new SecurityException("Unable to load secret signing key: not a master key 0x" +
        signingKey.getKeyID().toHexString.substring(8).toUpperCase())
    signingKey
  }
}

/**
 * Identificator for the fuzzy group of users.
 */
object ID {
  implicit def ID2implementation(i: ID.type): ID = i.inner

  /** Get ID implementation. */
  def inner = DI.implementation

  /** ID interface. */
  trait Interface {
    /** Universe public key. */
    def thatPublicKey: PGPPublicKey
    /** Encryption public key of fuzzy group.*/
    def thisPublicEncryptionKey: PGPPublicKey
    /** Public key ring of fuzzy group. */
    def thisPublicKeyRing: PGPPublicKeyRing
    /** Signing public key of fuzzy group.*/
    def thisPublicSigningKey: PGPPublicKey
    /** Encryption secret key of fuzzy group.*/
    def thisSecretEncryptionKey: PGPSecretKey
    /** Secret key ring of fuzzy group. */
    def thisSecretKeyRing: PGPSecretKeyRing
    /** Signing secret key of fuzzy group.*/
    def thisSecretSigningKey: PGPSecretKey
  }
  /**
   * Dependency injection routines
   */
  private object DI extends DependencyInjection.PersistentInjectable {
    /** ID implementation. */
    lazy val implementation = injectOptional[ID] getOrElse new ID
  }
}
