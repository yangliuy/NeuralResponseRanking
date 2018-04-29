import os

def run_command(command, job_name, output_file, error_file, partition, num_task, time, mem_per_cpu, gpu_num):
    bash_file_lines = "#!/bin/bash\n#\n"

    bash_file_lines += '#SBATCH --job-name=' + job_name + '\n'
    bash_file_lines += '#SBATCH --output=' + output_file + '\n'
    bash_file_lines += '#SBATCH -e ' + error_file + '\n'
    bash_file_lines += '#SBATCH --partition=' + partition + '\n'
    bash_file_lines += '#SBATCH --ntasks=' + num_task + '\n'
    bash_file_lines += '#SBATCH --time=' + time + '\n'
    bash_file_lines += '#SBATCH --mem-per-cpu=' + mem_per_cpu + '\n'
    bash_file_lines += 'module load python/2.7.12\n'
    bash_file_lines += command + '\n'
    bash_file_lines += 'exit' + '\n'

    bash_file_name = 'submit_slurm_job.sh'
    with open(bash_file_name, 'w') as f_out:
        f_out.write(bash_file_lines)
        print bash_file_lines

    cmd = 'sbatch -p ' + partition + ' --gres=gpu:' + str(gpu_num) + ' ' + bash_file_name
    print cmd
    os.system(cmd)

ms_path = '../data/ms_v2/ModelInput/dmn_model_input/'
udc_path = '../data/udc/ModelInput/dmn_model_input/'
phase = 'train'
rid = 21

batch_size_dict = {
    'ms_v2': '50',
    'udc': '200'
}

# DMN-KD
submited_job_count = 0
for data in list(['ms_v2']): # 'udc' 'ms'
    for model_name in list(['dmn_cnn_kd_ablation']):
        data_path = ms_path if data == 'ms_v2' else udc_path
        for model_setting in list(['m1', 'm2', 'm3', 'cosine', 'bilinear', 'm1m3', 'm2m3']):
            cur_data_res_path = '../data/' + data + '/ModelRes/'
            weights_file = cur_data_res_path + model_name  + '-modelabl-' + str(model_setting) + '-run' + str(rid) + '.weights'
            save_path = cur_data_res_path + model_name + '-modelabl-' + str(model_setting)  + '-run' + str(rid) + '.predict.test.txt'
            job_name = 'python-' + data + '-' + str(model_name) + '-modelabl-' + str(model_setting) + '-run' + str(rid)
            output_file = '../log/res-' + job_name + '.log'
            error_file = '../log/res-' + job_name + '.err'
            partition = 'titanx-long'
            num_task = '1'
            time = '06-00:00:00'
            mem_per_cpu = '40000'
            gpu_num = 1 # CUDA_VISIBLE_DEVICES=0
            batch_size = batch_size_dict[data]
            command = 'python -u main_conversation_qa.py --phase ' + phase + ' --model_file ./config/' + data + \
                        '/' + model_name + '.config' + ' --or_cmd True' + \
                       ' --valid_batch_list ' + batch_size +' --test_batch_list ' + batch_size +' --predict_batch_list ' \
                        + batch_size + ' --train_batch_size ' + batch_size + \
                        ' --weights_file ' + weights_file + ' --save_path ' + save_path
            # pass parameters for model ablation
            if model_setting in list(['m1', 'm2', 'm3', 'm1m3', 'm2m3']):
                command += ' --cross_matrix ' + model_setting
            else:
                command += ' --inter_type ' + model_setting
            print 'run command: ', command
            submited_job_count += 1
            run_command(command, job_name, output_file, error_file, partition, num_task, time, mem_per_cpu, gpu_num)

vocab_size_dict = {
    'udc_body':102503,
    'udc_title':97512,
    'ms_v2_body':30542,
    'ms_title':34679,
}

# DMN-PRF
embed_size = 200
for data in list(['ms_v2']): # 2 # 'udc'
    for model_name in list([ 'dmn_cnn_ablation']): # 2 'dmn_gru'
        for prf_type in list(['body']): # 2
            for model_setting in list(['m1', 'm2', 'cosine', 'bilinear']):
                cur_data_path = '../data/' + data + '/ModelInput/dmn_prf_model_input_' + prf_type + '/'
                cur_data_res_path = '../data/' + data + '/ModelRes/'
                embed_path = cur_data_path + 'cut_embed_mikolov_200d_no_readvocab.txt'
                test_relation_file = cur_data_path + 'relation_test.txt.fd'
                predict_relation_file = cur_data_path + 'relation_test.txt.fd'
                train_relation_file = cur_data_path + 'relation_train.txt.fd'
                valid_relation_file = cur_data_path + 'relation_valid.txt.fd'
                vocab_size = vocab_size_dict[data + '_' + prf_type]
                text1_corpus = cur_data_path + 'corpus_preprocessed.txt'
                text2_corpus = cur_data_path + 'corpus_preprocessed.txt'
                batch_size = batch_size_dict[data]

                weights_file = cur_data_res_path + model_name + '_prf_' + prf_type + '-modelabl-' + str(model_setting) + '-run' + str(rid) +  '.weights'
                save_path = cur_data_res_path + model_name + '_prf_' + prf_type + '-modelabl-' + str(model_setting) + '-run' + str(rid) +  '.predict.test.txt'
                job_name = 'python-' + data + '-' + str(model_name) + '_prf_' + prf_type + '-modelabl-' + str(model_setting) + '-run' + str(rid)
                output_file = '../log/res-' + job_name + '.log'
                error_file = '../log/res-' + job_name + '.err'
                partition = 'titanx-long'
                num_task = '1'
                time = '06-00:00:00'
                mem_per_cpu = '40000'
                gpu_num = 1 # CUDA_VISIBLE_DEVICES=0
                command = 'python -u main_conversation_qa.py --phase ' + phase + ' --model_file ./config/' + data + \
                            '/' + model_name + '.config' + ' --embed_path ' + embed_path + ' --embed_size ' + str(embed_size) + \
                            ' --test_relation_file ' + test_relation_file + ' --predict_relation_file ' + predict_relation_file + \
                            ' --train_relation_file ' + train_relation_file + ' --valid_relation_file ' + valid_relation_file + \
                            ' --vocab_size ' + str(vocab_size) + ' --text1_corpus ' + text1_corpus + ' --text2_corpus ' + text2_corpus + \
                            ' --weights_file ' + weights_file + ' --save_path ' + save_path + ' --or_cmd ' + str(True) + \
                            ' --valid_batch_list ' + batch_size +' --test_batch_list ' + batch_size +' --predict_batch_list ' \
                            + batch_size + ' --train_batch_size ' + batch_size
                # pass parameters for model ablation
                if model_setting in list(['m1', 'm2']):
                    command += ' --cross_matrix ' + model_setting
                else:
                    command += ' --inter_type ' + model_setting
                print 'run command: ', command
                run_command(command, job_name, output_file, error_file, partition, num_task, time, mem_per_cpu, gpu_num)






