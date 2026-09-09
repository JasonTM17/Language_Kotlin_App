-- =============================================================================
-- LinguaAI demo seed data (V2)
-- Per language: 30 vocabulary, 10 grammar, 5 lessons, 3 quizzes.
-- Japanese levels: N5/N4/N3. English levels: A1/A2/B1.
-- =============================================================================

-- ------------------------------ languages ------------------------------------
INSERT INTO languages (id, code, name, levels) VALUES
(1, 'ja', 'Japanese', 'N5,N4,N3'),
(2, 'en', 'English', 'A1,A2,B1');

-- ------------------------------ japanese vocabulary (1-30) -------------------
INSERT INTO vocabularies (id, language_id, level, word, reading, pronunciation, meaning, example, example_translation, category, created_at) VALUES
(1, 1, 'N3', '環境', 'かんきょう', 'kankyou', 'Môi trường', '環境を守ることは大切です。', 'Bảo vệ môi trường là điều quan trọng.', 'Society', CURRENT_TIMESTAMP),
(2, 1, 'N3', '経済', 'けいざい', 'keizai', 'Kinh tế', '日本の経済について勉強しています。', 'Tôi đang nghiên cứu về kinh tế Nhật Bản.', 'Society', CURRENT_TIMESTAMP),
(3, 1, 'N3', '教育', 'きょういく', 'kyouiku', 'Giáo dục', '教育を受ける権利があります。', 'Có quyền được nhận giáo dục.', 'Society', CURRENT_TIMESTAMP),
(4, 1, 'N5', '仕事', 'しごと', 'shigoto', 'Công việc', '仕事は9時に始まります。', 'Công việc bắt đầu lúc 9 giờ.', 'Work', CURRENT_TIMESTAMP),
(5, 1, 'N5', '家族', 'かぞく', 'kazoku', 'Gia đình', '家族と一緒に夕食を食べます。', 'Tôi ăn tối cùng gia đình.', 'Family', CURRENT_TIMESTAMP),
(6, 1, 'N5', '友達', 'ともだち', 'tomodachi', 'Bạn bè', '友達と映画を見ました。', 'Tôi đã xem phim với bạn.', 'Family', CURRENT_TIMESTAMP),
(7, 1, 'N5', '学校', 'がっこう', 'gakkou', 'Trường học', '学校は8時に始まります。', 'Trường học bắt đầu lúc 8 giờ.', 'Education', CURRENT_TIMESTAMP),
(8, 1, 'N5', '先生', 'せんせい', 'sensei', 'Giáo viên', '先生に質問しました。', 'Tôi đã hỏi giáo viên.', 'Education', CURRENT_TIMESTAMP),
(9, 1, 'N5', '学生', 'がくせい', 'gakusei', 'Học sinh, sinh viên', '私は大学の学生です。', 'Tôi là sinh viên đại học.', 'Education', CURRENT_TIMESTAMP),
(10, 1, 'N5', '電話', 'でんわ', 'denwa', 'Điện thoại', '後で電話してください。', 'Vui lòng gọi điện sau.', 'Daily', CURRENT_TIMESTAMP),
(11, 1, 'N5', '食べ物', 'たべもの', 'tabemono', 'Đồ ăn', '日本の食べ物が好きです。', 'Tôi thích đồ ăn Nhật.', 'Food', CURRENT_TIMESTAMP),
(12, 1, 'N5', '水', 'みず', 'mizu', 'Nước', '水を飲みたいです。', 'Tôi muốn uống nước.', 'Food', CURRENT_TIMESTAMP),
(13, 1, 'N5', '天気', 'てんき', 'tenki', 'Thời tiết', '明日の天気はどうですか。', 'Thời tiết ngày mai thế nào?', 'Nature', CURRENT_TIMESTAMP),
(14, 1, 'N5', '雨', 'あめ', 'ame', 'Mưa', '雨が降っています。', 'Trời đang mưa.', 'Nature', CURRENT_TIMESTAMP),
(15, 1, 'N5', '猫', 'ねこ', 'neko', 'Con mèo', '猫が家の前で寝ています。', 'Con mèo đang ngủ trước nhà.', 'Animals', CURRENT_TIMESTAMP),
(16, 1, 'N5', '犬', 'いぬ', 'inu', 'Con chó', '公園で犬を散歩させます。', 'Tôi dắt chó đi dạo ở công viên.', 'Animals', CURRENT_TIMESTAMP),
(17, 1, 'N4', '病院', 'びょういん', 'byouin', 'Bệnh viện', '病院へ検査を受けに行きます。', 'Tôi đi bệnh viện để khám.', 'Places', CURRENT_TIMESTAMP),
(18, 1, 'N4', '銀行', 'ぎんこう', 'ginkou', 'Ngân hàng', '銀行でお金を送金しました。', 'Tôi đã chuyển tiền ở ngân hàng.', 'Places', CURRENT_TIMESTAMP),
(19, 1, 'N5', '駅', 'えき', 'eki', 'Ga tàu', '駅まで歩いて10分です。', 'Đi bộ 10 phút đến ga.', 'Places', CURRENT_TIMESTAMP),
(20, 1, 'N4', '空港', 'くうこう', 'kuukou', 'Sân bay', '空港までタクシーで行きます。', 'Tôi đi sân bay bằng taxi.', 'Travel', CURRENT_TIMESTAMP),
(21, 1, 'N4', '旅行', 'りょこう', 'ryokou', 'Chuyến du lịch', '夏休みに旅行に行きます。', 'Tôi sẽ đi du lịch vào kỳ nghỉ hè.', 'Travel', CURRENT_TIMESTAMP),
(22, 1, 'N4', '予約', 'よやく', 'yoyaku', 'Sự đặt trước', 'レストランを予約しました。', 'Tôi đã đặt nhà hàng.', 'Travel', CURRENT_TIMESTAMP),
(23, 1, 'N4', '約束', 'やくそく', 'yakusoku', 'Lời hứa, hẹn', '約束を忘れないでください。', 'Xin đừng quên lời hứa.', 'Daily', CURRENT_TIMESTAMP),
(24, 1, 'N4', '準備', 'じゅんび', 'junbi', 'Sự chuẩn bị', 'テストの準備をしています。', 'Tôi đang chuẩn bị cho kỳ thi.', 'Daily', CURRENT_TIMESTAMP),
(25, 1, 'N3', '経験', 'けいけん', 'keiken', 'Kinh nghiệm', '海外で働いた経験があります。', 'Tôi có kinh nghiệm làm việc ở nước ngoài.', 'Abstract', CURRENT_TIMESTAMP),
(26, 1, 'N3', '機会', 'きかい', 'kikai', 'Cơ hội', 'もう一度挑戦する機会がありました。', 'Tôi đã có cơ hội thử lại một lần nữa.', 'Abstract', CURRENT_TIMESTAMP),
(27, 1, 'N3', '理由', 'りゆう', 'riyuu', 'Lý do', '遅れた理由を説明してください。', 'Hãy giải thích lý do đến muộn.', 'Abstract', CURRENT_TIMESTAMP),
(28, 1, 'N4', '大切', 'たいせつ', 'taisetsu', 'Quan trọng', '時間を大切にしてください。', 'Hãy trân trọng thời gian.', 'Adjective', CURRENT_TIMESTAMP),
(29, 1, 'N5', '好き', 'すき', 'suki', 'Thích', '音楽を聞くのが好きです。', 'Tôi thích nghe nhạc.', 'Adjective', CURRENT_TIMESTAMP),
(30, 1, 'N4', '頑張る', 'がんばる', 'ganbaru', 'Cố gắng', '明日の試験、頑張ります。', 'Tôi sẽ cố gắng cho kỳ thi ngày mai.', 'Verb', CURRENT_TIMESTAMP);

-- ------------------------------ english vocabulary (31-60) -------------------
INSERT INTO vocabularies (id, language_id, level, word, reading, pronunciation, meaning, example, example_translation, category, created_at) VALUES
(31, 2, 'A1', 'environment', NULL, '/ɪnˈvaɪrənmənt/', 'Môi trường', 'We must protect the environment.', 'Chúng ta phải bảo vệ môi trường.', 'Society', CURRENT_TIMESTAMP),
(32, 2, 'B1', 'economy', NULL, '/ɪˈkɒnəmi/', 'Nền kinh tế', 'The economy is growing slowly.', 'Nền kinh tế đang tăng trưởng chậm.', 'Society', CURRENT_TIMESTAMP),
(33, 2, 'B1', 'education', NULL, '/ˌedʒuˈkeɪʃn/', 'Giáo dục', 'Education opens many doors.', 'Giáo dục mở ra nhiều cơ hội.', 'Society', CURRENT_TIMESTAMP),
(34, 2, 'A1', 'job', NULL, '/dʒɒb/', 'Công việc', 'She found a new job.', 'Cô ấy đã tìm được công việc mới.', 'Work', CURRENT_TIMESTAMP),
(35, 2, 'A1', 'family', NULL, '/ˈfæməli/', 'Gia đình', 'My family lives in Hanoi.', 'Gia đình tôi sống ở Hà Nội.', 'Family', CURRENT_TIMESTAMP),
(36, 2, 'A1', 'friend', NULL, '/frend/', 'Bạn', 'He is my best friend.', 'Anh ấy là bạn thân của tôi.', 'Family', CURRENT_TIMESTAMP),
(37, 2, 'A1', 'school', NULL, '/skuːl/', 'Trường học', 'The children walk to school.', 'Các em đi bộ đến trường.', 'Education', CURRENT_TIMESTAMP),
(38, 2, 'A1', 'teacher', NULL, '/ˈtiːtʃə/', 'Giáo viên', 'The teacher explained the lesson.', 'Giáo viên đã giải thích bài học.', 'Education', CURRENT_TIMESTAMP),
(39, 2, 'A1', 'student', NULL, '/ˈstjuːdnt/', 'Học sinh, sinh viên', 'She is a medical student.', 'Cô ấy là sinh viên y khoa.', 'Education', CURRENT_TIMESTAMP),
(40, 2, 'A1', 'phone', NULL, '/fəʊn/', 'Điện thoại', 'My phone is charging.', 'Điện thoại của tôi đang sạc.', 'Daily', CURRENT_TIMESTAMP),
(41, 2, 'A1', 'food', NULL, '/fuːd/', 'Đồ ăn', 'Vietnamese food is delicious.', 'Đồ ăn Việt Nam rất ngon.', 'Food', CURRENT_TIMESTAMP),
(42, 2, 'A1', 'water', NULL, '/ˈwɔːtə/', 'Nước', 'Could I have some water?', 'Cho tôi một ít nước được không?', 'Food', CURRENT_TIMESTAMP),
(43, 2, 'A1', 'weather', NULL, '/ˈweðə/', 'Thời tiết', 'The weather is sunny today.', 'Hôm nay thời tiết nắng.', 'Nature', CURRENT_TIMESTAMP),
(44, 2, 'A1', 'rain', NULL, '/reɪn/', 'Mưa', 'The rain stopped an hour ago.', 'Cơn mưa dừng lại cách đây một giờ.', 'Nature', CURRENT_TIMESTAMP),
(45, 2, 'A1', 'cat', NULL, '/kæt/', 'Con mèo', 'The cat is sleeping on the sofa.', 'Con mèo đang ngủ trên ghế.', 'Animals', CURRENT_TIMESTAMP),
(46, 2, 'A1', 'dog', NULL, '/dɒg/', 'Con chó', 'They adopted a rescue dog.', 'Họ đã nhận nuôi một chú chó cứu hộ.', 'Animals', CURRENT_TIMESTAMP),
(47, 2, 'A1', 'hospital', NULL, '/ˈhɒspɪtl/', 'Bệnh viện', 'He was taken to hospital.', 'Anh ấy đã được đưa đến bệnh viện.', 'Places', CURRENT_TIMESTAMP),
(48, 2, 'A2', 'bank', NULL, '/bæŋk/', 'Ngân hàng', 'I need to go to the bank.', 'Tôi cần đến ngân hàng.', 'Places', CURRENT_TIMESTAMP),
(49, 2, 'A1', 'station', NULL, '/ˈsteɪʃn/', 'Ga, trạm', 'The station is two blocks away.', 'Ga cách đây hai dãy nhà.', 'Places', CURRENT_TIMESTAMP),
(50, 2, 'A2', 'airport', NULL, '/ˈeəpɔːt/', 'Sân bay', 'We arrived at the airport early.', 'Chúng tôi đến sân bay sớm.', 'Travel', CURRENT_TIMESTAMP),
(51, 2, 'A1', 'trip', NULL, '/trɪp/', 'Chuyến đi', 'How was your trip to Da Lat?', 'Chuyến đi Đà Lạt của bạn thế nào?', 'Travel', CURRENT_TIMESTAMP),
(52, 2, 'A2', 'reservation', NULL, '/ˌrezəˈveɪʃn/', 'Sự đặt chỗ', 'I made a reservation for two.', 'Tôi đã đặt chỗ cho hai người.', 'Travel', CURRENT_TIMESTAMP),
(53, 2, 'A2', 'promise', NULL, '/ˈprɒmɪs/', 'Lời hứa', 'Keep your promise.', 'Hãy giữ lời hứa của bạn.', 'Daily', CURRENT_TIMESTAMP),
(54, 2, 'A2', 'preparation', NULL, '/ˌprepəˈreɪʃn/', 'Sự chuẩn bị', 'Preparation is the key to success.', 'Chuẩn bị là chìa khóa của thành công.', 'Daily', CURRENT_TIMESTAMP),
(55, 2, 'B1', 'experience', NULL, '/ɪkˈspɪəriəns/', 'Kinh nghiệm', 'She has five years of experience.', 'Cô ấy có năm năm kinh nghiệm.', 'Abstract', CURRENT_TIMESTAMP),
(56, 2, 'B1', 'opportunity', NULL, '/ˌɒpəˈtjuːnəti/', 'Cơ hội', 'This is a great opportunity.', 'Đây là một cơ hội tuyệt vời.', 'Abstract', CURRENT_TIMESTAMP),
(57, 2, 'B1', 'reason', NULL, '/ˈriːzn/', 'Lý do', 'Tell me the reason for the delay.', 'Hãy cho tôi lý do của sự chậm trễ.', 'Abstract', CURRENT_TIMESTAMP),
(58, 2, 'A2', 'important', NULL, '/ɪmˈpɔːtnt/', 'Quan trọng', 'It is important to practice daily.', 'Việc luyện tập mỗi ngày là quan trọng.', 'Adjective', CURRENT_TIMESTAMP),
(59, 2, 'A1', 'like', NULL, '/laɪk/', 'Thích', 'I like learning languages.', 'Tôi thích học ngoại ngữ.', 'Adjective', CURRENT_TIMESTAMP),
(60, 2, 'A2', 'effort', NULL, '/ˈefət/', 'Nỗ lực', 'Your effort will pay off.', 'Nỗ lực của bạn sẽ được đền đáp.', 'Verb', CURRENT_TIMESTAMP);

-- ------------------------------ japanese grammar (1-10) ----------------------
INSERT INTO grammar_lessons (id, language_id, level, title, structure, meaning, usage_notes, examples, notes, difficulty, created_at) VALUES
(1, 1, 'N3', '～ように', 'V辞書 + ように / Vない + ように', 'Để / sao cho', 'Diễn tả mục đích, mong muốn một hành động hoặc kết quả xảy ra, thường dùng với động từ khả năng, động từ tự nhiên.', '忘れないようにメモします。| Ghi chú để không quên. / 風邪をひかないように気をつけて。| Cẩn thận để không bị cảm.', 'Khác ～ために: ように dùng khi mục đích là trạng thái/khả năng (の+ thư), ために dùng cho hành động có chủ đích rõ ràng.', 3, CURRENT_TIMESTAMP),
(2, 1, 'N3', '～わけではない', '普通形 + わけではない', 'Không hẳn là', 'Phủ định một cách tuyệt đối giả định trước đó; phủ định một phần.', '高いですが、買えないわけではありません。| Dù đắt nhưng không hẳn là tôi không mua được.', 'Thường đi với 特に, 別に để nhấn mạnh sắc thái phủ định nhẹ.', 3, CURRENT_TIMESTAMP),
(3, 1, 'N3', '～ことになる', 'V辞書/ない形 + ことになる', 'Trở thành, được quyết định', 'Diễn tả quyết định hoặc kết quả do hoàn cảnh bên ngoài đưa ra (không phải ý muốn chủ quan).', '来月、大阪に転勤することになりました。| Tháng sau tôi được điều chuyển đến Osaka.', 'Đối lập với ～ことにする (quyết định chủ quan).', 4, CURRENT_TIMESTAMP),
(4, 1, 'N3', '～ために', 'Nの + ために / V辞書 + ために', 'Vì / để', 'Diễn tả mục đích hoặc nguyên nhân; mục đích phải là hành động chủ đích.', '健康のために毎日運動します。| Vì sức khỏe, tôi vận động mỗi ngày.', 'Không dùng với động từ khả năng; đó là lĩnh vực của ～ように.', 2, CURRENT_TIMESTAMP),
(5, 1, 'N3', '～ば (điều kiện)', 'Vば形', 'Nếu', 'Điều kiện chung: nếu điều kiện được thỏa thì kết quả xảy ra.', '時間があれば、一緒に行きましょう。| Nếu có thời gian, cùng đi nhé.', 'Khi vế sau là ý chí/mong muốn chỉ dùng trong vài cấu trúc cụ thể.', 3, CURRENT_TIMESTAMP),
(6, 1, 'N4', '～ても', 'Vて形 + も / Aいくて+も', 'Dù... cũng', 'Diễn tả sự nhượng bộ: dù điều kiện đó xảy ra, kết quả vẫn không đổi.', '雨が降っても行きます。| Dù mưa tôi vẫn đi.', 'Dạng danh từ/tính từ đuôi な dùng でも.', 2, CURRENT_TIMESTAMP),
(7, 1, 'N4', '～ながら', 'Vます (bỏ ます) + ながら', 'Vừa... vừa...', 'Hai hành động diễn ra đồng thời, hành động chính ở vế sau.', '音楽を聞きながら勉強します。| Tôi học vừa nghe nhạc.', 'Chủ thể hai vế phải là một người.', 2, CURRENT_TIMESTAMP),
(8, 1, 'N5', '～たい', 'Vます (bỏ ます) + たい', 'Muốn (làm gì)', 'Diễn tả mong muốn chủ quan của người nói.', '日本へ行きたいです。| Tôi muốn đi Nhật Bản.', 'Ngôi thứ ba dùng たがっている.', 1, CURRENT_TIMESTAMP),
(9, 1, 'N5', '～てもいい', 'Vて形 + もいい', 'Được phép', 'Xin phép hoặc cho phép làm gì đó.', '写真を撮ってもいいですか。| Tôi chụp ảnh được không?', 'Phủ định: ～てもいいですか dùng để xin phép lịch sự.', 1, CURRENT_TIMESTAMP),
(10, 1, 'N4', '～なければならない', 'Vない (bỏ ない) + なければならない', 'Phải (bắt buộc)', 'Diễn tả nghĩa vụ bắt buộc phải làm.', 'レポートを提出しなければなりません。| Tôi phải nộp báo cáo.', 'Nói ngắn: ～なきゃ (thân mật).', 2, CURRENT_TIMESTAMP);

-- ------------------------------ english grammar (11-20) ----------------------
INSERT INTO grammar_lessons (id, language_id, level, title, structure, meaning, usage_notes, examples, notes, difficulty, created_at) VALUES
(11, 2, 'A1', 'Present Simple', 'S + V(s/es)', 'Thì hiện tại đơn', 'Diễn tả thói quen, sự thật chung. Thêm s/es với ngôi thứ ba số ít.', 'I study English every day. | Tôi học tiếng Anh mỗi ngày. / She works at a bank. | Cô ấy làm việc ở ngân hàng.', 'Dùng do/does cho câu phủ định và nghi vấn.', 1, CURRENT_TIMESTAMP),
(12, 2, 'A1', 'Present Continuous', 'S + am/is/are + V-ing', 'Thì hiện tại tiếp diễn', 'Diễn tả hành động đang diễn ra ngay lúc nói hoặc xung quanh hiện tại.', 'They are watching TV now. | Họ đang xem TV bây giờ.', 'Không dùng với động từ trạng thái như know, like.', 1, CURRENT_TIMESTAMP),
(13, 2, 'A1', 'Past Simple', 'S + V2/ed', 'Thì quá khứ đơn', 'Diễn tả hành động đã kết thúc tại thời điểm xác định trong quá khứ.', 'We visited Kyoto last year. | Chúng tôi đã ghé Kyoto năm ngoái.', 'Động từ bất quy tắc cần học thuộc (go - went).', 2, CURRENT_TIMESTAMP),
(14, 2, 'A2', 'Be going to (future)', 'S + am/is/are going to + V', 'Tương lai với going to', 'Diễn tả kế hoạch hoặc dự đoán dựa trên bằng chứng hiện tại.', 'I am going to travel this summer. | Tôi sẽ đi du lịch mùa hè này.', 'Khác will: going to là kế hoạch đã định trước.', 2, CURRENT_TIMESTAMP),
(15, 2, 'A2', 'Comparatives', 'Adj-er + than / more + Adj + than', 'So sánh hơn', 'So sánh hai đối tượng. Tính từ ngắn thêm -er, dài thêm more.', 'This book is more interesting than that one. | Quyển này thú vị hơn quyển kia.', 'Tính từ hai âm tiết kết thúc bằng -y: happy -> happier.', 2, CURRENT_TIMESTAMP),
(16, 2, 'B1', 'Present Perfect', 'S + have/has + V3/ed', 'Thì hiện tại hoàn thành', 'Hành động xảy ra trong quá khứ nhưng còn liên quan hiện tại, hoặc trải nghiệm chưa xác định thời gian.', 'I have finished my homework. | Tôi đã làm xong bài tập.', 'Đi với already, yet, just, ever, never, since, for.', 3, CURRENT_TIMESTAMP),
(17, 2, 'B1', 'First Conditional', 'If + S + V(s), S + will + V', 'Câu điều kiện loại 1', 'Điều kiện có thể xảy ra ở hiện tại hoặc tương lai.', 'If it rains, we will stay home. | Nếu trời mưa, chúng ta sẽ ở nhà.', 'Mệnh đề if dùng hiện tại đơn, KHÔNG dùng will ở mệnh đề if.', 3, CURRENT_TIMESTAMP),
(18, 2, 'B1', 'Passive Voice', 'S + be + V3/ed (+ by O)', 'Câu bị động', 'Nhấn mạnh đối tượng chịu tác động thay vì người thực hiện.', 'The report was written yesterday. | Báo cáo đã được viết ngày hôm qua.', 'Chỉ thêm by + tác nhân khi cần thiết.', 3, CURRENT_TIMESTAMP),
(19, 2, 'B1', 'Relative Clauses', 'N + who/which/that + clause', 'Mệnh đề quan hệ', 'Bổ sung thông tin cho danh từ: người dùng who, vật dùng which.', 'The engineer who fixed our server is very kind. | Kỹ sư đã sửa máy chủ rất tử tế.', 'that thay được cả who/which trong mệnh đề xác định.', 4, CURRENT_TIMESTAMP),
(20, 2, 'B1', 'Used to', 'S + used to + V', 'Đã từng (thói quen quá khứ)', 'Thói quen hoặc trạng thái trong quá khứ nay không còn.', 'I used to live in Da Nang. | Tôi từng sống ở Đà Nẵng.', 'Phủ định: did not use to. Nhiễu với be used to + V-ing (quen với).', 3, CURRENT_TIMESTAMP);

-- ------------------------------ lessons --------------------------------------
INSERT INTO lessons (id, language_id, level, title, description, type, estimated_minutes, difficulty, content, created_at, updated_at) VALUES
(1, 1, 'N5', 'Chào hỏi và giới thiệu bản thân', 'あいさつと自己紹介 - Làm quen với các câu chào hỏi cơ bản nhất trong tiếng Nhật.', 'VOCABULARY', 10, 1, 'Xin chào: こんにちは (konnichiwa)\nChào buổi sáng: おはようございます (ohayou gozaimasu)\nCảm ơn: ありがとうございます (arigatou gozaimasu)\nTôi tên là...: 〜と申します (〜 to moushimasu)\nRất vui được gặp bạn: はじめまして (hajimemashito)', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(2, 1, 'N4', 'Gọi món tại nhà hàng', 'レストランで注文 - Hội thoại mẫu khi ăn uống, đặt món và tính tiền.', 'CONVERSATION', 15, 2, 'Nhân viên: いらっしゃいませ。何名様ですか。\nKhách: 二人です。\nNhân viên: お注文はお決まりですか。\nKhách: ラーメンを二つお願いします。\nNhân viên: かしこまりました。\n\nTừ khóa: お願いします (làm ơn), おすすめ (món gợi ý), お会計 (hóa đơn)', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(3, 1, 'N5', 'Trợ từ は và が', '「は」と「が」の違い - Trợ từ quan trọng nhất tiếng Nhật, khác biệt cốt lõi.', 'GRAMMAR', 12, 3, 'は (wa): đánh dấu CHỦ ĐỀ của câu - những gì đang nói về.\n私[は]学生です = Về tôi, tôi là học sinh.\n\nが (ga): đánh dấu CHỦ NGỮ được nhấn mạnh, thông tin mới.\n誰が来ましたか。 - 田中さん[が]来ました。(Ai đến? - Là Tanaka đến)\n\nQuy tắc nhanh: câu trả lời cho WHO/WHAT -> dùng が; giới thiệu chủ đề -> dùng は.', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(4, 1, 'N4', 'Thông báo trên tàu điện', '電車のアナウンス - Luyện nghe qua các thông báo tại ga tàu.', 'LISTENING', 10, 2, '次は、新宿、新宿です。出口は右側です。\n(Vượt tới là Shinjuku, Shinjuku. Cửa ra ở phía bên phải.)\n\nこの電車は、各駅停車、東京駅行きです。\n(Tàu này là tàu dừng mọi ga, hướng Ga Tokyo.)\n\nお忘れ物ないようご注意ください。\n(Xin lưu ý đừng bỏ quên hành lý.)', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(5, 1, 'N3', 'Nhật ký một ngày của tôi', '私の一日 - Đọc hiểu văn phong tự sự mô tả thói quen hằng ngày.', 'READING', 15, 3, '朝六時に起きて、まずコーヒーを飲みます。それから、三十分ほどジョギングをします。会社は九時に始まりますが、私は八時に出かけます。夜は日本語を勉強してから寝ます。毎日同じことの繰り返しですが、充実しています。\n\nTừ vựng chính: 充実 (juujitsu: trọn vẹn), 繰り返し (kurikaeshi: sự lặp lại)', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(6, 2, 'A1', 'Introducing yourself', 'Practice the most common self-introduction patterns.', 'CONVERSATION', 10, 1, 'Hi, my name is Son.\nI am from Vietnam.\nI am a student.\nNice to meet you!\n\nPattern: My name is + name. / I am from + place. / I am a + job.', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(7, 2, 'A1', 'At the supermarket', 'Learn food and shopping vocabulary in context.', 'VOCABULARY', 10, 1, 'A: Excuse me, where is the milk?\nB: It is in aisle three, next to the bread.\nA: Thank you! How much is this rice?\nB: It is two dollars.\n\nWords: aisle, checkout, receipt, discount', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(8, 2, 'A2', 'Making travel plans', 'Read a short travel conversation and plan a trip.', 'READING', 15, 2, 'Mai: I am going to visit Hoi An next weekend. Would you like to come?\nDavid: Sure! How are we getting there?\nMai: We could take the train. It is cheaper than flying.\nDavid: Good idea. Should we book a hotel in advance?\nMai: Yes, homestays fill up quickly on weekends.', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(9, 2, 'B1', 'Writing a professional email', 'Structure and polite phrases for work emails.', 'READING', 12, 3, 'Subject: Meeting request - Q3 roadmap\n\nDear Mr. Tanaka,\n\nI hope this email finds you well. I am writing to request a meeting to discuss the Q3 roadmap. Would Tuesday at 2 PM work for you?\n\nBest regards,\nSon', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(10, 2, 'A2', 'Small talk at work', 'Common icebreakers and short conversations with colleagues.', 'CONVERSATION', 10, 2, 'A: How was your weekend?\nB: It was great. I went hiking with friends. How about you?\nA: I just stayed home and watched movies.\nB: Sometimes that is exactly what you need!', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- ------------------------------ quizzes --------------------------------------
INSERT INTO quizzes (id, language_id, level, title, description, created_at) VALUES
(1, 1, 'N5', 'N5 Vocabulary Check', 'Kiểm tra 5 từ vựng N5 cơ bản.', CURRENT_TIMESTAMP),
(2, 1, 'N4', 'N4 Vocabulary Check', 'Kiểm tra 5 từ vựng N4.', CURRENT_TIMESTAMP),
(3, 1, 'N3', 'N3 Grammar Challenge', 'Kiểm tra ngữ pháp N3: ように, わけではない, ことになる.', CURRENT_TIMESTAMP),
(4, 2, 'A1', 'A1 Vocabulary Check', 'Five essential A1 words.', CURRENT_TIMESTAMP),
(5, 2, 'A2', 'A2 Vocabulary Check', 'Five useful A2 words.', CURRENT_TIMESTAMP),
(6, 2, 'B1', 'B1 Grammar Challenge', 'Present perfect, conditional and passive.', CURRENT_TIMESTAMP);

INSERT INTO quiz_questions (id, quiz_id, question_type, prompt, options, correct_answer, explanation, position) VALUES
(1, 1, 'MULTIPLE_CHOICE', '環境 có nghĩa là gì?', '["Công việc","Môi trường","Kinh tế","Giáo dục"]', 'Môi trường', '環境 (かんきょう) = môi trường.', 1),
(2, 1, 'MULTIPLE_CHOICE', '家族 đọc là gì?', '["かぞく","かてい","かじょう","がぞく"]', 'かぞく', '家族 = かぞく (kazoku) = gia đình.', 2),
(3, 1, 'MULTIPLE_CHOICE', '先生 có nghĩa là gì?', '["Học sinh","Bác sĩ","Giáo viên","Kỹ sư"]', 'Giáo viên', '先生 (せんせい) = giáo viên.', 3),
(4, 1, 'MULTIPLE_CHOICE', 'Từ nào nghĩa là "nước"', '["木","水","火","金"]', '水', '水 (みず) = nước.', 4),
(5, 1, 'MULTIPLE_CHOICE', '駅とは何ですか。', '["Sân bay","Ga tàu","Bệnh viện","Trường học"]', 'Ga tàu', '駅 (えき) = ga tàu.', 5),
(6, 2, 'MULTIPLE_CHOICE', '予約 có nghĩa là gì?', '[''Lời hứa'',''Sự đặt trước'',''Sự chuẩn bị'',''Kinh nghiệm'']', 'Sự đặt trước', '予約 (よやく) = đặt trước (phòng, vé, nhà hàng).', 1),
(7, 2, 'MULTIPLE_CHOICE', 'Điền vào chỗ trống: 約束を（　　）でください。', '[''忘れない'',''忘れたい'',''忘れる'',''忘れた'']', '忘れない', 'Dạng ないで của lời yêu cầu: xin đừng quên lời hứa.', 2),
(8, 2, 'MULTIPLE_CHOICE', '空港 có nghĩa là gì?', '[''Sân bay'',''Bãi đỗ xe'',''Nhà ga xe buýt'',''Cảng biển'']', 'Sân bay', '空港 (くうこう) = sân bay.', 3),
(9, 2, 'MULTIPLE_CHOICE', 'Sắc thái của 頑張る là gì?', '[''Từ bỏ'',''Cố gắng'',''Nghỉ ngơi'',''Thắng cuộc'']', 'Cố gắng', '頑張る (がんばる) = cố gắng, gan lỳ.', 4),
(10, 2, 'MULTIPLE_CHOICE', 'Điền trợ từ: 病院（　）行きます。', '[''を'',''に'',''が'',''の'']', 'に', 'Điểm đến + に + 行きます (đi đến...).', 5),
(11, 3, 'MULTIPLE_CHOICE', '風邪を（　　）ように気をつけて。', '[''ひか'',''ひかない'',''ひいた'',''ひこう'']', 'ひかない', 'ように với phủ định: cẩn thận để KHÔNG bị cảm.', 1),
(12, 3, 'MULTIPLE_CHOICE', '「買えないわけではない」nghĩa là gì?', '[''Không thể mua'',''Không hẳn là không mua được'',''Chắc chắn sẽ mua'',''Từ chối mua'']', 'Không hẳn là không mua được', 'わけではない = phủ định tuyệt đối -> không hẳn là.', 2),
(13, 3, 'MULTIPLE_CHOICE', 'Quyết định do công ty đưa ra (không phải ý chủ quan) dùng cấu trúc nào?', '[''ことにする'',''ことになる'',''ようにする'',''ことにしている'']', 'ことになる', 'ことになる = trở thành/quyết định từ hoàn cảnh bên ngoài.', 3),
(14, 3, 'MULTIPLE_CHOICE', 'Hành động có chủ đích rõ ràng làm mục đích: dùng cấu trúc nào?', '[''ように'',''ために'',''そうに'',''みたいに'']', 'ために', 'ために dùng cho mục đích là hành động chủ đích.', 4),
(15, 3, 'MULTIPLE_CHOICE', '雨が降っても（　　）。', '[''行きます'',''行きません'',''行きたくない'',''行った'']', '行きます', 'ても = dù... cũng: dù mưa vẫn đi.', 5),
(16, 4, 'MULTIPLE_CHOICE', 'What does "teacher" mean?', '[''Học sinh'',''Giáo viên'',''Bác sĩ'',''Người bán hàng'']', 'Giáo viên', 'A teacher works at a school.', 1),
(17, 4, 'MULTIPLE_CHOICE', '"Trường học" in English is...', '[''station'',''school'',''market'',''library'']', 'school', 'School = trường học.', 2),
(18, 4, 'MULTIPLE_CHOICE', 'Choose the correct sentence.', '[''She work at a bank.'',''She works at a bank.'',''She working at a bank.'',''She worked bank.'']', 'She works at a bank.', 'Present simple, third person singular adds -s.', 3),
(19, 4, 'MULTIPLE_CHOICE', 'Which word means "nước"?', '[''fire'',''wind'',''water'',''earth'']', 'water', 'Water = nước.', 4),
(20, 4, 'MULTIPLE_CHOICE', '"My family lives in Hanoi." - family nghĩa là gì?', '[''Bạn bè'',''Gia đình'',''Công ty'',''Trường'']', 'Gia đình', 'Family = gia đình.', 5),
(21, 5, 'MULTIPLE_CHOICE', 'The plane leaves from the ___ .', '[''airport'',''bakery'',''library'',''cinema'']', 'airport', 'Planes leave from airports.', 1),
(22, 5, 'MULTIPLE_CHOICE', '"Reservation" nghĩa là gì?', '[''Sự đặt chỗ'',''Hoá đơn'',''Sự hoàn trả'',''Lời mời'']', 'Sự đặt chỗ', 'A reservation secures your table or room.', 2),
(23, 5, 'MULTIPLE_CHOICE', 'Choose the correct future plan sentence.', '[''I going to travel.'',''I am going to travel.'',''I am go to travel.'',''I am going travel.'']', 'I am going to travel.', 'be going to + verb (base form).', 3),
(24, 5, 'MULTIPLE_CHOICE', 'This book is ___ than that one.', '[''interesting'',''more interesting'',''most interesting'',''interestinger'']', 'more interesting', 'Long adjectives use more + adjective + than.', 4),
(25, 5, 'MULTIPLE_CHOICE', '"Promise" nghĩa là gì?', '[''Lời hứa'',''Sự chuẩn bị'',''Kỷ niệm'',''Lời khuyên'']', 'Lời hứa', 'Keep your promise = giữ lời hứa.', 5),
(26, 6, 'MULTIPLE_CHOICE', 'I ___ my homework already.', '[''finish'',''finished'',''have finished'',''am finishing'']', 'have finished', 'Present perfect with already.', 1),
(27, 6, 'MULTIPLE_CHOICE', 'If it rains, we ___ at home.', '[''stay'',''will stay'',''stayed'',''would stay'']', 'will stay', 'First conditional: if + present simple, will + verb.', 2),
(28, 6, 'MULTIPLE_CHOICE', 'The report ___ yesterday.', '[''wrote'',''was written'',''is written'',''has wrote'']', 'was written', 'Passive voice in past simple.', 3),
(29, 6, 'MULTIPLE_CHOICE', 'She has lived here ___ 2019.', '[''for'',''since'',''from'',''during'']', 'since', 'since + point in time; for + duration.', 4),
(30, 6, 'MULTIPLE_CHOICE', 'I ___ to smoke, but I quit last year.', '[''use to'',''used to'',''am used to'',''using to'']', 'used to', 'used to + verb = past habit.', 5);
